package com.aitrainercrm.platform.sequence.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.sequence.dto.CreateSequenceRequest;
import com.aitrainercrm.platform.sequence.dto.CreateSequenceStepRequest;
import com.aitrainercrm.platform.sequence.dto.UpdateSequenceRequest;
import com.aitrainercrm.platform.sequence.dto.UpdateSequenceStepRequest;
import com.aitrainercrm.platform.sequence.entity.Sequence;
import com.aitrainercrm.platform.sequence.entity.SequenceStep;
import com.aitrainercrm.platform.sequence.repository.SequenceRepository;
import com.aitrainercrm.platform.sequence.repository.SequenceStepRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sequence catalog and its steps - see {@link Sequence}'s javadoc and V32's migration comment.
 * No {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} call here,
 * same reasoning {@code CourseService}'s javadoc gives for {@code Course}: no {@code ownerId} on
 * {@link Sequence}, so the controller's TEAM/DEPARTMENT/ORGANIZATION {@code @PreAuthorize} is the
 * whole authorization story. Steps are managed the same way {@code QuoteService} manages line items
 * - add/update/remove one at a time, gated on the same UPDATE permission as the parent Sequence,
 * with no permission of their own.
 */
@Service
@RequiredArgsConstructor
public class SequenceService {

    private final SequenceRepository sequenceRepository;
    private final SequenceStepRepository sequenceStepRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Sequence> list(UserPrincipal principal, Pageable pageable) {
        return sequenceRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Sequence> listActive(UserPrincipal principal) {
        return sequenceRepository.findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(principal.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public Sequence get(UserPrincipal principal, UUID sequenceId) {
        return findOrThrow(principal.getOrganizationId(), sequenceId);
    }

    @Transactional(readOnly = true)
    public List<SequenceStep> getSteps(UserPrincipal principal, UUID sequenceId) {
        get(principal, sequenceId); // re-validates existence
        return sequenceStepRepository.findBySequenceIdOrderByStepOrderAsc(sequenceId);
    }

    @Transactional
    public Sequence create(UserPrincipal principal, CreateSequenceRequest request) {
        Sequence sequence = new Sequence(principal.getOrganizationId(), request.name());
        sequence.setDescription(request.description());
        sequenceRepository.save(sequence);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Sequence", sequence.getId()));
        return sequence;
    }

    @Transactional
    public Sequence update(UserPrincipal principal, UUID sequenceId, UpdateSequenceRequest request) {
        Sequence sequence = findOrThrow(principal.getOrganizationId(), sequenceId);
        sequence.setName(request.name());
        sequence.setDescription(request.description());
        sequence.setActive(request.active());
        sequenceRepository.save(sequence);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Sequence", sequence.getId()));
        return sequence;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID sequenceId) {
        Sequence sequence = findOrThrow(principal.getOrganizationId(), sequenceId);
        sequence.setDeletedAt(Instant.now());
        sequenceRepository.save(sequence);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Sequence", sequenceId));
    }

    /** New steps are always appended at the end - stepOrder is the current step count, so reordering (not built in this pass) would be the only way to move one earlier. */
    @Transactional
    public SequenceStep addStep(UserPrincipal principal, UUID sequenceId, CreateSequenceStepRequest request) {
        Sequence sequence = findOrThrow(principal.getOrganizationId(), sequenceId);
        int nextOrder = (int) sequenceStepRepository.countBySequenceId(sequence.getId());

        SequenceStep step = new SequenceStep(sequence.getId(), nextOrder, request.type(), request.dayOffset());
        step.setSubject(request.subject());
        step.setBody(request.body());
        sequenceStepRepository.save(step);
        return step;
    }

    @Transactional
    public SequenceStep updateStep(UserPrincipal principal, UUID sequenceId, UUID stepId, UpdateSequenceStepRequest request) {
        findOrThrow(principal.getOrganizationId(), sequenceId);
        SequenceStep step = findStepOrThrow(sequenceId, stepId);

        step.setType(request.type());
        step.setDayOffset(request.dayOffset());
        step.setSubject(request.subject());
        step.setBody(request.body());
        sequenceStepRepository.save(step);
        return step;
    }

    @Transactional
    public void removeStep(UserPrincipal principal, UUID sequenceId, UUID stepId) {
        findOrThrow(principal.getOrganizationId(), sequenceId);
        SequenceStep step = findStepOrThrow(sequenceId, stepId);
        sequenceStepRepository.delete(step);
    }

    /** Package-visible for {@code SequenceEnrollmentService}, which needs a sequence's real step count/order without duplicating this lookup. */
    Sequence findOrThrow(UUID organizationId, UUID sequenceId) {
        return sequenceRepository.findActiveByIdAndOrganizationId(sequenceId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Sequence", sequenceId));
    }

    List<SequenceStep> stepsOf(UUID sequenceId) {
        return sequenceStepRepository.findBySequenceIdOrderByStepOrderAsc(sequenceId);
    }

    private SequenceStep findStepOrThrow(UUID sequenceId, UUID stepId) {
        return sequenceStepRepository.findById(stepId)
                .filter(step -> step.getSequenceId().equals(sequenceId))
                .orElseThrow(() -> new ResourceNotFoundException("SequenceStep", stepId));
    }
}
