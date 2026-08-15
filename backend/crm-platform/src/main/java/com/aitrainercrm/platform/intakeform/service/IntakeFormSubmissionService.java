package com.aitrainercrm.platform.intakeform.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.intakeform.dto.CreateIntakeFormSubmissionRequest;
import com.aitrainercrm.platform.intakeform.dto.UpdateIntakeFormSubmissionRequest;
import com.aitrainercrm.platform.intakeform.entity.IntakeFormSubmission;
import com.aitrainercrm.platform.intakeform.repository.IntakeFormSubmissionRepository;
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
 * One client's completed intake form - see {@link IntakeFormSubmission}'s javadoc and V60's
 * migration comment for the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION
 * record-level authorization shape as {@code ProgressPhotoService}, with {@code resolveOwner}
 * defaulting a null {@code ownerId} to the caller. Injects {@link IntakeFormService} and calls its
 * package-private {@code findOrThrow} to validate a submission's parent form exists - same
 * package-co-location precedent {@code RoomBookingService} established for {@code Room} and
 * {@code EquipmentReservationService} established for {@code Equipment}.
 */
@Service
@RequiredArgsConstructor
public class IntakeFormSubmissionService {

    private static final Permission.Resource RESOURCE = Permission.Resource.INTAKE_FORM_SUBMISSION;

    private final IntakeFormSubmissionRepository intakeFormSubmissionRepository;
    private final IntakeFormService intakeFormService;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<IntakeFormSubmission> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> intakeFormSubmissionRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> intakeFormSubmissionRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public IntakeFormSubmission get(UserPrincipal principal, UUID intakeFormSubmissionId) {
        IntakeFormSubmission submission = findOrThrow(principal.getOrganizationId(), intakeFormSubmissionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, submission.getOwnerId());
        return submission;
    }

    @Transactional
    public IntakeFormSubmission create(UserPrincipal principal, CreateIntakeFormSubmissionRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        intakeFormService.findOrThrow(principal.getOrganizationId(), request.formId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        IntakeFormSubmission submission = new IntakeFormSubmission(principal.getOrganizationId(), request.formId(), request.contactId(), ownerId);
        submission.setResponses(request.responses());
        submission.setNotes(request.notes());
        intakeFormSubmissionRepository.save(submission);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "IntakeFormSubmission", submission.getId()));
        return submission;
    }

    /** submittedAt is never changed here - same "point-in-time fact" restraint {@code ProgressPhotoService#update} applies to takenAt. */
    @Transactional
    public IntakeFormSubmission update(UserPrincipal principal, UUID intakeFormSubmissionId, UpdateIntakeFormSubmissionRequest request) {
        IntakeFormSubmission submission = findOrThrow(principal.getOrganizationId(), intakeFormSubmissionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, submission.getOwnerId());

        submission.setResponses(request.responses());
        submission.setNotes(request.notes());
        intakeFormSubmissionRepository.save(submission);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "IntakeFormSubmission", submission.getId()));
        return submission;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID intakeFormSubmissionId) {
        IntakeFormSubmission submission = findOrThrow(principal.getOrganizationId(), intakeFormSubmissionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, submission.getOwnerId());

        submission.setDeletedAt(Instant.now());
        intakeFormSubmissionRepository.save(submission);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "IntakeFormSubmission", intakeFormSubmissionId));
    }

    private IntakeFormSubmission findOrThrow(UUID organizationId, UUID intakeFormSubmissionId) {
        return intakeFormSubmissionRepository.findActiveByIdAndOrganizationId(intakeFormSubmissionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("IntakeFormSubmission", intakeFormSubmissionId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " intake form submissions you manage");
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
