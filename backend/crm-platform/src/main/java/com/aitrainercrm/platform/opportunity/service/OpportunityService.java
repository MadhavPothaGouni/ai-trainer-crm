package com.aitrainercrm.platform.opportunity.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.opportunity.dto.CreateOpportunityRequest;
import com.aitrainercrm.platform.opportunity.dto.UpdateOpportunityRequest;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deals. {@code stage} is intentionally not editable through {@link #update} -
 * see {@link #updateStage}, the only path that changes it, because moving
 * into (or out of) a closed stage also has to maintain
 * {@code actualCloseDate} correctly, and a plain field-by-field PUT has no
 * good way to express "only touch this field with this side effect."
 */
@Service
@RequiredArgsConstructor
public class OpportunityService {

    private static final Permission.Resource RESOURCE = Permission.Resource.OPPORTUNITY;

    private final OpportunityRepository opportunityRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Opportunity> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> opportunityRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> opportunityRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Opportunity get(UserPrincipal principal, UUID opportunityId) {
        Opportunity opportunity = findOrThrow(principal.getOrganizationId(), opportunityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, opportunity.getOwnerId());
        return opportunity;
    }

    @Transactional
    public Opportunity create(UserPrincipal principal, CreateOpportunityRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertAccountInOrganization(principal.getOrganizationId(), request.accountId());
        assertContactInOrganization(principal.getOrganizationId(), request.primaryContactId());

        Opportunity opportunity = new Opportunity(principal.getOrganizationId(), request.accountId(), request.name(), ownerId);
        opportunity.setPrimaryContactId(request.primaryContactId());
        opportunity.setAmount(request.amount());
        opportunity.setCurrency(request.currency());
        opportunity.setExpectedCloseDate(request.expectedCloseDate());
        opportunity.setDescription(request.description());
        opportunityRepository.save(opportunity);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Opportunity", opportunity.getId()));
        return opportunity;
    }

    @Transactional
    public Opportunity update(UserPrincipal principal, UUID opportunityId, UpdateOpportunityRequest request) {
        Opportunity opportunity = findOrThrow(principal.getOrganizationId(), opportunityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, opportunity.getOwnerId());
        assertAccountInOrganization(principal.getOrganizationId(), request.accountId());
        assertContactInOrganization(principal.getOrganizationId(), request.primaryContactId());

        opportunity.setAccountId(request.accountId());
        opportunity.setPrimaryContactId(request.primaryContactId());
        opportunity.setName(request.name());
        opportunity.setAmount(request.amount());
        opportunity.setCurrency(request.currency());
        opportunity.setExpectedCloseDate(request.expectedCloseDate());
        opportunity.setDescription(request.description());
        opportunityRepository.save(opportunity);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Opportunity", opportunity.getId()));
        return opportunity;
    }

    @Transactional
    public Opportunity updateStage(UserPrincipal principal, UUID opportunityId, Opportunity.Stage newStage) {
        Opportunity opportunity = findOrThrow(principal.getOrganizationId(), opportunityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, opportunity.getOwnerId());

        opportunity.setStage(newStage);
        // actualCloseDate tracks "when did this deal actually close" - set the moment it
        // enters a closed stage, cleared if it's ever reopened (a rep un-closing a deal
        // marked CLOSED_LOST by mistake, for instance).
        opportunity.setActualCloseDate(newStage.isClosed() ? LocalDate.now() : null);
        opportunityRepository.save(opportunity);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Opportunity", opportunity.getId()));
        return opportunity;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID opportunityId) {
        Opportunity opportunity = findOrThrow(principal.getOrganizationId(), opportunityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, opportunity.getOwnerId());

        opportunity.setDeletedAt(Instant.now());
        opportunityRepository.save(opportunity);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Opportunity", opportunity.getId()));
    }

    @Transactional
    public Opportunity assignOwner(UserPrincipal principal, UUID opportunityId, UUID newOwnerId) {
        Opportunity opportunity = findOrThrow(principal.getOrganizationId(), opportunityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, opportunity.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        opportunity.setOwnerId(newOwnerId);
        opportunityRepository.save(opportunity);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "Opportunity", opportunity.getId(), newOwnerId));
        return opportunity;
    }

    private Opportunity findOrThrow(UUID organizationId, UUID opportunityId) {
        return opportunityRepository.findActiveByIdAndOrganizationId(opportunityId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity", opportunityId));
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

    private void assertContactInOrganization(UUID organizationId, UUID contactId) {
        if (contactId == null) return;
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }
}
