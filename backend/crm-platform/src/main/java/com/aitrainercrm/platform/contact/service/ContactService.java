package com.aitrainercrm.platform.contact.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.dto.CreateContactRequest;
import com.aitrainercrm.platform.contact.dto.UpdateContactRequest;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CONTACT;

    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Contact> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> contactRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> contactRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Contact get(UserPrincipal principal, UUID contactId) {
        Contact contact = findOrThrow(principal.getOrganizationId(), contactId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, contact.getOwnerId());
        return contact;
    }

    @Transactional
    public Contact create(UserPrincipal principal, CreateContactRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertAccountInOrganization(principal.getOrganizationId(), request.accountId());

        Contact contact = new Contact(principal.getOrganizationId(), request.firstName(), request.lastName(), ownerId);
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setTitle(request.title());
        contact.setDescription(request.description());
        contact.setAccountId(request.accountId());
        contactRepository.save(contact);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Contact", contact.getId()));
        return contact;
    }

    @Transactional
    public Contact update(UserPrincipal principal, UUID contactId, UpdateContactRequest request) {
        Contact contact = findOrThrow(principal.getOrganizationId(), contactId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, contact.getOwnerId());
        assertAccountInOrganization(principal.getOrganizationId(), request.accountId());

        contact.setFirstName(request.firstName());
        contact.setLastName(request.lastName());
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setTitle(request.title());
        contact.setDescription(request.description());
        contact.setAccountId(request.accountId());
        contactRepository.save(contact);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Contact", contact.getId()));
        return contact;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID contactId) {
        Contact contact = findOrThrow(principal.getOrganizationId(), contactId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, contact.getOwnerId());

        contact.setDeletedAt(Instant.now());
        contactRepository.save(contact);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Contact", contact.getId()));
    }

    @Transactional
    public Contact assignOwner(UserPrincipal principal, UUID contactId, UUID newOwnerId) {
        Contact contact = findOrThrow(principal.getOrganizationId(), contactId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, contact.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        contact.setOwnerId(newOwnerId);
        contactRepository.save(contact);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "Contact", contact.getId(), newOwnerId));
        return contact;
    }

    private Contact findOrThrow(UUID organizationId, UUID contactId) {
        return contactRepository.findActiveByIdAndOrganizationId(contactId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));
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
        if (accountId == null) return;
        if (!accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(accountId, organizationId)) {
            throw new ResourceNotFoundException("Account", accountId);
        }
    }
}
