package com.aitrainercrm.platform.account.service;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.account.dto.UpdateAccountRequest;
import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
 * Companies. See ScopeAuthorizationService for how OWN/TEAM/DEPARTMENT/
 * ORGANIZATION-scoped ACCOUNT permissions actually get enforced here -
 * {@code @PreAuthorize} on the controller only proves the caller holds
 * *some* level of access; this class is what decides whether they hold
 * enough of it for *this particular* account.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Permission.Resource RESOURCE = Permission.Resource.ACCOUNT;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Account> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> accountRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> accountRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Account get(UserPrincipal principal, UUID accountId) {
        Account account = findOrThrow(principal.getOrganizationId(), accountId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, account.getOwnerId());
        return account;
    }

    @Transactional
    public Account create(UserPrincipal principal, CreateAccountRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());

        Account account = new Account(principal.getOrganizationId(), request.name(), ownerId);
        applyFields(account, request.industry(), request.website(), request.phone(), request.billingStreet(),
                request.billingCity(), request.billingState(), request.billingPostalCode(), request.billingCountry(),
                request.annualRevenue(), request.employeeCount(), request.description());
        accountRepository.save(account);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Account", account.getId()));
        return account;
    }

    @Transactional
    public Account update(UserPrincipal principal, UUID accountId, UpdateAccountRequest request) {
        Account account = findOrThrow(principal.getOrganizationId(), accountId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, account.getOwnerId());

        account.setName(request.name());
        applyFields(account, request.industry(), request.website(), request.phone(), request.billingStreet(),
                request.billingCity(), request.billingState(), request.billingPostalCode(), request.billingCountry(),
                request.annualRevenue(), request.employeeCount(), request.description());
        accountRepository.save(account);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Account", account.getId()));
        return account;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID accountId) {
        Account account = findOrThrow(principal.getOrganizationId(), accountId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, account.getOwnerId());

        account.setDeletedAt(Instant.now());
        accountRepository.save(account);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Account", account.getId()));
    }

    @Transactional
    public Account assignOwner(UserPrincipal principal, UUID accountId, UUID newOwnerId) {
        Account account = findOrThrow(principal.getOrganizationId(), accountId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, account.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        account.setOwnerId(newOwnerId);
        accountRepository.save(account);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "Account", account.getId(), newOwnerId));
        return account;
    }

    private Account findOrThrow(UUID organizationId, UUID accountId) {
        return accountRepository.findActiveByIdAndOrganizationId(accountId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    /**
     * Null in the request means "assign to me" - the common case (a rep creating their own
     * pipeline). A non-null, different-from-caller id means someone is being assigned on
     * someone else's behalf, which requires the broadest (ORGANIZATION) scope for this
     * action - a rep with only OWN/TEAM-scope CREATE can create records, just always owned
     * by themselves.
     */
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
        boolean exists = userRepository.findActiveById(userId)
                .map(u -> organizationId.equals(u.getOrganizationId()))
                .orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    private void applyFields(
            Account account, String industry, String website, String phone, String billingStreet, String billingCity,
            String billingState, String billingPostalCode, String billingCountry,
            BigDecimal annualRevenue, Integer employeeCount, String description) {
        account.setIndustry(industry);
        account.setWebsite(website);
        account.setPhone(phone);
        account.setBillingStreet(billingStreet);
        account.setBillingCity(billingCity);
        account.setBillingState(billingState);
        account.setBillingPostalCode(billingPostalCode);
        account.setBillingCountry(billingCountry);
        account.setAnnualRevenue(annualRevenue);
        account.setEmployeeCount(employeeCount);
        account.setDescription(description);
    }
}
