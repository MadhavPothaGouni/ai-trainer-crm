package com.aitrainercrm.platform.trainingsession.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.booking.repository.BookingLinkRepository;
import com.aitrainercrm.platform.booking.repository.BookingSlotRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.exercise.repository.ExerciseRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.trainingsession.dto.CreateTrainingSessionExerciseRequest;
import com.aitrainercrm.platform.trainingsession.dto.CreateTrainingSessionRequest;
import com.aitrainercrm.platform.trainingsession.dto.UpdateTrainingSessionExerciseRequest;
import com.aitrainercrm.platform.trainingsession.dto.UpdateTrainingSessionRequest;
import com.aitrainercrm.platform.trainingsession.entity.TrainingSession;
import com.aitrainercrm.platform.trainingsession.entity.TrainingSessionExercise;
import com.aitrainercrm.platform.trainingsession.repository.TrainingSessionExerciseRepository;
import com.aitrainercrm.platform.trainingsession.repository.TrainingSessionRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
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
 * Training sessions - see {@link TrainingSession}'s javadoc and V37's migration comment for the
 * backstory. Follows the exact same shape as {@code ClientGoalService}/{@code ContractService}:
 * OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization via
 * {@link ScopeAuthorizationService}, {@code resolveOwner} defaulting a null {@code ownerId} to
 * the caller. Per-session exercise entries (V39) are managed the same way {@code QuoteService}
 * manages line items - add/update/remove one at a time, gated on the same UPDATE permission as
 * the parent session, with no permission of their own.
 */
@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    private static final Permission.Resource RESOURCE = Permission.Resource.TRAINING_SESSION;

    private final TrainingSessionRepository trainingSessionRepository;
    private final TrainingSessionExerciseRepository trainingSessionExerciseRepository;
    private final ContactRepository contactRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BookingLinkRepository bookingLinkRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<TrainingSession> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> trainingSessionRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> trainingSessionRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public TrainingSession get(UserPrincipal principal, UUID trainingSessionId) {
        TrainingSession session = findOrThrow(principal.getOrganizationId(), trainingSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, session.getOwnerId());
        return session;
    }

    @Transactional(readOnly = true)
    public List<TrainingSessionExercise> getExercises(UserPrincipal principal, UUID trainingSessionId) {
        get(principal, trainingSessionId); // re-validates existence + READ access
        return trainingSessionExerciseRepository.findByTrainingSessionIdOrderBySequenceOrderAsc(trainingSessionId);
    }

    @Transactional
    public TrainingSession create(UserPrincipal principal, CreateTrainingSessionRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());
        assertBookingSlotInOrganization(principal.getOrganizationId(), request.bookingSlotId());

        TrainingSession session = new TrainingSession(principal.getOrganizationId(), request.contactId(), ownerId, request.startedAt());
        session.setBookingSlotId(request.bookingSlotId());
        session.setDurationMinutes(request.durationMinutes());
        session.setSessionType(request.sessionType());
        session.setFocusArea(request.focusArea());
        session.setClientRpe(request.clientRpe());
        session.setCoachNotes(request.coachNotes());
        trainingSessionRepository.save(session);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "TrainingSession", session.getId()));
        return session;
    }

    @Transactional
    public TrainingSession update(UserPrincipal principal, UUID trainingSessionId, UpdateTrainingSessionRequest request) {
        TrainingSession session = findOrThrow(principal.getOrganizationId(), trainingSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, session.getOwnerId());
        assertBookingSlotInOrganization(principal.getOrganizationId(), request.bookingSlotId());

        session.setBookingSlotId(request.bookingSlotId());
        session.setStartedAt(request.startedAt());
        session.setDurationMinutes(request.durationMinutes());
        session.setSessionType(request.sessionType());
        session.setFocusArea(request.focusArea());
        session.setClientRpe(request.clientRpe());
        session.setCoachNotes(request.coachNotes());
        trainingSessionRepository.save(session);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "TrainingSession", session.getId()));
        return session;
    }

    /**
     * No invalid-transition checks, same restraint {@code ContractService#updateStatus}'s javadoc
     * documents for contracts.status - a NO_SHOW logged in error can be corrected back to
     * SCHEDULED, no invalid-transition rule needed.
     */
    @Transactional
    public TrainingSession updateStatus(UserPrincipal principal, UUID trainingSessionId, TrainingSession.Status newStatus) {
        TrainingSession session = findOrThrow(principal.getOrganizationId(), trainingSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, session.getOwnerId());

        session.setStatus(newStatus);
        trainingSessionRepository.save(session);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "TrainingSession", session.getId()));
        return session;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID trainingSessionId) {
        TrainingSession session = findOrThrow(principal.getOrganizationId(), trainingSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, session.getOwnerId());

        session.setDeletedAt(Instant.now());
        trainingSessionRepository.save(session);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "TrainingSession", session.getId()));
    }

    /** New exercises are always appended at the end - sequenceOrder is the current entry count, same restraint SequenceService#addStep documents (no reordering built in this pass). */
    @Transactional
    public TrainingSessionExercise addExercise(UserPrincipal principal, UUID trainingSessionId, CreateTrainingSessionExerciseRequest request) {
        TrainingSession session = findOrThrow(principal.getOrganizationId(), trainingSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, session.getOwnerId());
        if (request.exerciseId() != null) {
            assertExerciseInOrganization(principal.getOrganizationId(), request.exerciseId());
        }
        int nextOrder = (int) trainingSessionExerciseRepository.countByTrainingSessionId(trainingSessionId);

        TrainingSessionExercise exercise = new TrainingSessionExercise(
                trainingSessionId, request.exerciseName(), nextOrder, request.setsCompleted(), request.repsCompleted());
        exercise.setExerciseId(request.exerciseId());
        exercise.setWeightValue(request.weightValue());
        exercise.setWeightUnit(request.weightUnit());
        exercise.setNotes(request.notes());
        trainingSessionExerciseRepository.save(exercise);
        return exercise;
    }

    @Transactional
    public TrainingSessionExercise updateExercise(
            UserPrincipal principal, UUID trainingSessionId, UUID exerciseEntryId, UpdateTrainingSessionExerciseRequest request) {
        TrainingSession session = findOrThrow(principal.getOrganizationId(), trainingSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, session.getOwnerId());
        if (request.exerciseId() != null) {
            assertExerciseInOrganization(principal.getOrganizationId(), request.exerciseId());
        }
        TrainingSessionExercise exercise = findExerciseOrThrow(trainingSessionId, exerciseEntryId);

        exercise.setExerciseId(request.exerciseId());
        exercise.setExerciseName(request.exerciseName());
        exercise.setSetsCompleted(request.setsCompleted());
        exercise.setRepsCompleted(request.repsCompleted());
        exercise.setWeightValue(request.weightValue());
        exercise.setWeightUnit(request.weightUnit());
        exercise.setNotes(request.notes());
        trainingSessionExerciseRepository.save(exercise);
        return exercise;
    }

    @Transactional
    public void removeExercise(UserPrincipal principal, UUID trainingSessionId, UUID exerciseEntryId) {
        TrainingSession session = findOrThrow(principal.getOrganizationId(), trainingSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, session.getOwnerId());
        TrainingSessionExercise exercise = findExerciseOrThrow(trainingSessionId, exerciseEntryId);
        trainingSessionExerciseRepository.delete(exercise);
    }

    private TrainingSessionExercise findExerciseOrThrow(UUID trainingSessionId, UUID exerciseEntryId) {
        return trainingSessionExerciseRepository.findByIdAndTrainingSessionId(exerciseEntryId, trainingSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSessionExercise", exerciseEntryId));
    }

    private void assertExerciseInOrganization(UUID organizationId, UUID exerciseId) {
        exerciseRepository.findActiveByIdAndOrganizationId(exerciseId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", exerciseId));
    }

    private TrainingSession findOrThrow(UUID organizationId, UUID trainingSessionId) {
        return trainingSessionRepository.findActiveByIdAndOrganizationId(trainingSessionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", trainingSessionId));
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

    /** BookingSlot itself has no organizationId column (only bookingLinkId) - see BookingSlot's own shape (V33) - so this joins through the slot's parent BookingLink to check tenancy, the same indirection its own controller-level access has to do. */
    private void assertBookingSlotInOrganization(UUID organizationId, UUID bookingSlotId) {
        if (bookingSlotId == null) return;
        var slot = bookingSlotRepository.findById(bookingSlotId).orElseThrow(() -> new ResourceNotFoundException("BookingSlot", bookingSlotId));
        bookingLinkRepository
                .findActiveByIdAndOrganizationId(slot.getBookingLinkId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingSlot", bookingSlotId));
    }
}
