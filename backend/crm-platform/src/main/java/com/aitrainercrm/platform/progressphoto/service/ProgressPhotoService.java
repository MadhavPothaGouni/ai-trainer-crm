package com.aitrainercrm.platform.progressphoto.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.progressphoto.dto.CreateProgressPhotoRequest;
import com.aitrainercrm.platform.progressphoto.dto.UpdateProgressPhotoRequest;
import com.aitrainercrm.platform.progressphoto.entity.ProgressPhoto;
import com.aitrainercrm.platform.progressphoto.repository.ProgressPhotoRepository;
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
 * A client's physical-progress photo - see {@link ProgressPhoto}'s javadoc and V55's migration
 * comment for the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION record-level
 * authorization shape as {@code LockerAssignmentService}, with {@code resolveOwner} defaulting a
 * null {@code ownerId} to the caller, but with no {@code updateStatus} counterpart - see
 * {@link ProgressPhoto}'s javadoc for why a progress photo has no status lifecycle.
 */
@Service
@RequiredArgsConstructor
public class ProgressPhotoService {

    private static final Permission.Resource RESOURCE = Permission.Resource.PROGRESS_PHOTO;

    private final ProgressPhotoRepository progressPhotoRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ProgressPhoto> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> progressPhotoRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> progressPhotoRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public ProgressPhoto get(UserPrincipal principal, UUID progressPhotoId) {
        ProgressPhoto photo = findOrThrow(principal.getOrganizationId(), progressPhotoId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, photo.getOwnerId());
        return photo;
    }

    @Transactional
    public ProgressPhoto create(UserPrincipal principal, CreateProgressPhotoRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(request.contactId(), principal.getOrganizationId())) {
            throw new ResourceNotFoundException("Contact", request.contactId());
        }

        ProgressPhoto photo = new ProgressPhoto(principal.getOrganizationId(), request.contactId(), ownerId, request.photoUrl());
        photo.setCategory(request.category());
        photo.setNotes(request.notes());
        progressPhotoRepository.save(photo);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ProgressPhoto", photo.getId()));
        return photo;
    }

    @Transactional
    public ProgressPhoto update(UserPrincipal principal, UUID progressPhotoId, UpdateProgressPhotoRequest request) {
        ProgressPhoto photo = findOrThrow(principal.getOrganizationId(), progressPhotoId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, photo.getOwnerId());

        photo.setPhotoUrl(request.photoUrl());
        photo.setCategory(request.category());
        photo.setNotes(request.notes());
        progressPhotoRepository.save(photo);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ProgressPhoto", photo.getId()));
        return photo;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID progressPhotoId) {
        ProgressPhoto photo = findOrThrow(principal.getOrganizationId(), progressPhotoId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, photo.getOwnerId());

        photo.setDeletedAt(Instant.now());
        progressPhotoRepository.save(photo);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ProgressPhoto", progressPhotoId));
    }

    private ProgressPhoto findOrThrow(UUID organizationId, UUID progressPhotoId) {
        return progressPhotoRepository.findActiveByIdAndOrganizationId(progressPhotoId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ProgressPhoto", progressPhotoId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " photos logged by yourself");
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
}
