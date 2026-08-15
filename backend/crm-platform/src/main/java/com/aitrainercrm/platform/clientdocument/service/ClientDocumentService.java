package com.aitrainercrm.platform.clientdocument.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.clientdocument.dto.CreateClientDocumentRequest;
import com.aitrainercrm.platform.clientdocument.dto.UpdateClientDocumentRequest;
import com.aitrainercrm.platform.clientdocument.entity.ClientDocument;
import com.aitrainercrm.platform.clientdocument.repository.ClientDocumentRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
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

/**
 * Client documents/waivers - see {@link ClientDocument}'s javadoc and V48's migration comment for
 * the backstory. Follows the exact same shape as {@code ClientGoalService}: OWN/TEAM/DEPARTMENT/
 * ORGANIZATION record-level authorization via {@link ScopeAuthorizationService},
 * {@code resolveOwner} defaulting a null {@code ownerId} to the caller.
 */
@Service
@RequiredArgsConstructor
public class ClientDocumentService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CLIENT_DOCUMENT;

    private final ClientDocumentRepository clientDocumentRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ClientDocument> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> clientDocumentRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> clientDocumentRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public ClientDocument get(UserPrincipal principal, UUID clientDocumentId) {
        ClientDocument document = findOrThrow(principal.getOrganizationId(), clientDocumentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, document.getOwnerId());
        return document;
    }

    @Transactional
    public ClientDocument create(UserPrincipal principal, CreateClientDocumentRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        ClientDocument document = new ClientDocument(principal.getOrganizationId(), request.contactId(), ownerId, request.title());
        document.setDocumentType(request.documentType());
        document.setExpiresAt(request.expiresAt());
        document.setFileUrl(request.fileUrl());
        document.setNotes(request.notes());
        clientDocumentRepository.save(document);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ClientDocument", document.getId()));
        return document;
    }

    @Transactional
    public ClientDocument update(UserPrincipal principal, UUID clientDocumentId, UpdateClientDocumentRequest request) {
        ClientDocument document = findOrThrow(principal.getOrganizationId(), clientDocumentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, document.getOwnerId());

        document.setDocumentType(request.documentType());
        document.setTitle(request.title());
        document.setExpiresAt(request.expiresAt());
        document.setFileUrl(request.fileUrl());
        document.setNotes(request.notes());
        clientDocumentRepository.save(document);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClientDocument", document.getId()));
        return document;
    }

    /**
     * No invalid-transition checks, same restraint {@code ClientGoalService#updateStatus}'s
     * javadoc documents - reinstating a REVOKED document to SIGNED is a legitimate correction.
     * {@code signedAt} is stamped the first time status moves to SIGNED and never overwritten
     * afterward.
     */
    @Transactional
    public ClientDocument updateStatus(UserPrincipal principal, UUID clientDocumentId, ClientDocument.Status newStatus) {
        ClientDocument document = findOrThrow(principal.getOrganizationId(), clientDocumentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, document.getOwnerId());

        if (newStatus == ClientDocument.Status.SIGNED && document.getSignedAt() == null) {
            document.setSignedAt(Instant.now());
        }
        document.setStatus(newStatus);
        clientDocumentRepository.save(document);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClientDocument", document.getId()));
        return document;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID clientDocumentId) {
        ClientDocument document = findOrThrow(principal.getOrganizationId(), clientDocumentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, document.getOwnerId());

        document.setDeletedAt(Instant.now());
        clientDocumentRepository.save(document);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ClientDocument", clientDocumentId));
    }

    private ClientDocument findOrThrow(UUID organizationId, UUID clientDocumentId) {
        return clientDocumentRepository.findActiveByIdAndOrganizationId(clientDocumentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientDocument", clientDocumentId));
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

    private void assertContactInOrganization(UUID organizationId, UUID contactId) {
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }
}
