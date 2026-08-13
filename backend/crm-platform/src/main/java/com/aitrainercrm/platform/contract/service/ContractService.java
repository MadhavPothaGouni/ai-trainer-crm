package com.aitrainercrm.platform.contract.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contract.dto.CreateContractRequest;
import com.aitrainercrm.platform.contract.dto.UpdateContractRequest;
import com.aitrainercrm.platform.contract.entity.Contract;
import com.aitrainercrm.platform.contract.repository.ContractRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contracts - see {@link Contract}'s javadoc and V35's migration comment for the backstory.
 * Follows the exact same shape as {@code TicketService}/{@code BookingLinkService}: OWN/TEAM/
 * DEPARTMENT/ORGANIZATION record-level authorization via {@link ScopeAuthorizationService},
 * {@code resolveOwner} defaulting a null {@code ownerId} to the caller.
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CONTRACT;

    private final ContractRepository contractRepository;
    private final AccountRepository accountRepository;
    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Contract> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> contractRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> contractRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Contract get(UserPrincipal principal, UUID contractId) {
        Contract contract = findOrThrow(principal.getOrganizationId(), contractId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, contract.getOwnerId());
        return contract;
    }

    @Transactional
    public Contract create(UserPrincipal principal, CreateContractRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertAccountInOrganization(principal.getOrganizationId(), request.accountId());
        assertOpportunityInOrganization(principal.getOrganizationId(), request.opportunityId());
        assertDatesValid(request.startDate(), request.endDate());
        assertRenewalTermPresentIfAutoRenew(request.autoRenew(), request.renewalTermMonths());
        assertContractNumberAvailable(principal.getOrganizationId(), request.contractNumber(), null);

        Contract contract = new Contract(
                principal.getOrganizationId(), request.accountId(), ownerId, request.contractNumber(), request.title(), request.startDate(), request.endDate());
        contract.setOpportunityId(request.opportunityId());
        if (request.totalValue() != null) {
            contract.setTotalValue(request.totalValue());
        }
        contract.setAutoRenew(request.autoRenew());
        contract.setRenewalTermMonths(request.renewalTermMonths());
        contract.setTerms(request.terms());
        contractRepository.save(contract);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Contract", contract.getId()));
        return contract;
    }

    @Transactional
    public Contract update(UserPrincipal principal, UUID contractId, UpdateContractRequest request) {
        Contract contract = findOrThrow(principal.getOrganizationId(), contractId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, contract.getOwnerId());
        assertOpportunityInOrganization(principal.getOrganizationId(), request.opportunityId());
        assertDatesValid(request.startDate(), request.endDate());
        assertRenewalTermPresentIfAutoRenew(request.autoRenew(), request.renewalTermMonths());
        assertContractNumberAvailable(principal.getOrganizationId(), request.contractNumber(), contractId);

        contract.setOpportunityId(request.opportunityId());
        contract.setContractNumber(request.contractNumber());
        contract.setTitle(request.title());
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        if (request.totalValue() != null) {
            contract.setTotalValue(request.totalValue());
        }
        contract.setAutoRenew(request.autoRenew());
        contract.setRenewalTermMonths(request.renewalTermMonths());
        contract.setTerms(request.terms());
        contractRepository.save(contract);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Contract", contract.getId()));
        return contract;
    }

    /**
     * No invalid-transition checks, same restraint {@code TicketService#updateStatus}'s javadoc
     * documents for tickets.status - a DRAFT contract can be corrected back from TERMINATED, a
     * RENEWED contract can be reopened if the follow-on deal falls through, and so on.
     * {@code signedAt} is stamped the first time status moves to ACTIVE and is never cleared or
     * overwritten afterward, since "when was this originally signed" shouldn't change just
     * because the contract later moves through other statuses.
     */
    @Transactional
    public Contract updateStatus(UserPrincipal principal, UUID contractId, Contract.Status newStatus) {
        Contract contract = findOrThrow(principal.getOrganizationId(), contractId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, contract.getOwnerId());

        if (newStatus == Contract.Status.ACTIVE && contract.getSignedAt() == null) {
            contract.setSignedAt(Instant.now());
        }
        contract.setStatus(newStatus);
        contractRepository.save(contract);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Contract", contract.getId()));
        return contract;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID contractId) {
        Contract contract = findOrThrow(principal.getOrganizationId(), contractId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, contract.getOwnerId());

        contract.setDeletedAt(Instant.now());
        contractRepository.save(contract);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Contract", contract.getId()));
    }

    private void assertDatesValid(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("CONTRACT_INVALID_DATES", "End date cannot be before start date", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertRenewalTermPresentIfAutoRenew(boolean autoRenew, Integer renewalTermMonths) {
        if (autoRenew && renewalTermMonths == null) {
            throw new BusinessException("CONTRACT_MISSING_RENEWAL_TERM", "A renewal term (in months) is required when auto-renew is enabled", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertContractNumberAvailable(UUID organizationId, String contractNumber, UUID excludingContractId) {
        boolean inUse = contractRepository.existsByOrganizationIdAndContractNumberAndDeletedAtIsNull(organizationId, contractNumber);
        if (!inUse) {
            return;
        }
        // A contract keeping its own number during an update isn't a conflict - only reject if some OTHER active contract already holds it.
        if (excludingContractId != null) {
            Contract existing = findOrThrow(organizationId, excludingContractId);
            if (existing.getContractNumber().equals(contractNumber)) {
                return;
            }
        }
        throw new DuplicateResourceException("A contract with this number already exists");
    }

    private Contract findOrThrow(UUID organizationId, UUID contractId) {
        return contractRepository.findActiveByIdAndOrganizationId(contractId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract", contractId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " records assigned to yourself");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedOwnerId);
        return requestedOwnerId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    private void assertAccountInOrganization(UUID organizationId, UUID accountId) {
        if (!accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(accountId, organizationId)) {
            throw new ResourceNotFoundException("Account", accountId);
        }
    }

    private void assertOpportunityInOrganization(UUID organizationId, UUID opportunityId) {
        if (opportunityId == null) return;
        if (!opportunityRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(opportunityId, organizationId)) {
            throw new ResourceNotFoundException("Opportunity", opportunityId);
        }
    }
}
