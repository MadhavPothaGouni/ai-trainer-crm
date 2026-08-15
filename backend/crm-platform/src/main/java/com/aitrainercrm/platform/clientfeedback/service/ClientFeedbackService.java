package com.aitrainercrm.platform.clientfeedback.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.clientfeedback.dto.CreateClientFeedbackRequest;
import com.aitrainercrm.platform.clientfeedback.dto.UpdateClientFeedbackRequest;
import com.aitrainercrm.platform.clientfeedback.entity.ClientFeedback;
import com.aitrainercrm.platform.clientfeedback.repository.ClientFeedbackRepository;
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
 * An NPS-style rating a client gave - see {@link ClientFeedback}'s javadoc and V66's migration
 * comment for the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION record-level
 * authorization shape as {@code NutritionLogService}, with {@code resolveOwner} defaulting a null
 * {@code ownerId} to the caller. No business-rule validation beyond the usual existence checks and
 * the DTO-level 0-10 range on {@code npsScore} - this is the simplest shape in the batch, same as
 * {@code NutritionLogService}.
 */
@Service
@RequiredArgsConstructor
public class ClientFeedbackService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CLIENT_FEEDBACK;

    private final ClientFeedbackRepository clientFeedbackRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ClientFeedback> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> clientFeedbackRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> clientFeedbackRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public ClientFeedback get(UserPrincipal principal, UUID clientFeedbackId) {
        ClientFeedback feedback = findOrThrow(principal.getOrganizationId(), clientFeedbackId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, feedback.getOwnerId());
        return feedback;
    }

    @Transactional
    public ClientFeedback create(UserPrincipal principal, CreateClientFeedbackRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        ClientFeedback feedback = new ClientFeedback(
                principal.getOrganizationId(), request.contactId(), ownerId, request.npsScore(), request.relatedType(), request.submittedAt());
        feedback.setComments(request.comments());
        clientFeedbackRepository.save(feedback);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ClientFeedback", feedback.getId()));
        return feedback;
    }

    @Transactional
    public ClientFeedback update(UserPrincipal principal, UUID clientFeedbackId, UpdateClientFeedbackRequest request) {
        ClientFeedback feedback = findOrThrow(principal.getOrganizationId(), clientFeedbackId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, feedback.getOwnerId());

        feedback.setNpsScore(request.npsScore());
        feedback.setRelatedType(request.relatedType());
        feedback.setSubmittedAt(request.submittedAt());
        feedback.setComments(request.comments());
        clientFeedbackRepository.save(feedback);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClientFeedback", feedback.getId()));
        return feedback;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID clientFeedbackId) {
        ClientFeedback feedback = findOrThrow(principal.getOrganizationId(), clientFeedbackId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, feedback.getOwnerId());

        feedback.setDeletedAt(Instant.now());
        clientFeedbackRepository.save(feedback);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ClientFeedback", clientFeedbackId));
    }

    private ClientFeedback findOrThrow(UUID organizationId, UUID clientFeedbackId) {
        return clientFeedbackRepository.findActiveByIdAndOrganizationId(clientFeedbackId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientFeedback", clientFeedbackId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " feedback you manage");
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
