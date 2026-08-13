package com.aitrainercrm.platform.sequence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.sequence.dto.CreateSequenceEnrollmentRequest;
import com.aitrainercrm.platform.sequence.entity.Sequence;
import com.aitrainercrm.platform.sequence.entity.SequenceEnrollment;
import com.aitrainercrm.platform.sequence.entity.SequenceStep;
import com.aitrainercrm.platform.sequence.repository.SequenceEnrollmentRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * See {@link SequenceEnrollmentService}'s javadoc for the shape this mirrors ({@code
 * TicketService}/{@code CourseEnrollmentService}). {@link SequenceService} is mocked wholesale, the
 * same reasoning {@code CourseEnrollmentServiceTest} gives for mocking {@code CourseService}.
 */
@ExtendWith(MockitoExtension.class)
class SequenceEnrollmentServiceTest {

    @Mock private SequenceEnrollmentRepository sequenceEnrollmentRepository;
    @Mock private SequenceService sequenceService;
    @Mock private LeadRepository leadRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private SequenceEnrollmentService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SequenceEnrollmentService(
                sequenceEnrollmentRepository, sequenceService, leadRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "rep@example.com", organizationId, List.of());
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        UUID sequenceId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        when(sequenceService.findOrThrow(organizationId, sequenceId)).thenReturn(sequence(sequenceId));
        when(leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(leadId, organizationId)).thenReturn(true);
        when(sequenceEnrollmentRepository.existsByOrganizationIdAndSequenceIdAndTargetTypeAndTargetIdAndDeletedAtIsNull(
                organizationId, sequenceId, SequenceEnrollment.TargetType.LEAD, leadId))
                .thenReturn(false);

        SequenceEnrollment result = service.create(
                principal(callerId), new CreateSequenceEnrollmentRequest(sequenceId, SequenceEnrollment.TargetType.LEAD, leadId, null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getTargetId()).isEqualTo(leadId);
        assertThat(result.getStatus()).isEqualTo(SequenceEnrollment.Status.ACTIVE);
        verify(sequenceEnrollmentRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID sequenceId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(sequenceService.findOrThrow(organizationId, sequenceId)).thenReturn(sequence(sequenceId));
        when(leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(leadId, organizationId)).thenReturn(true);
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(
                principal(callerId), new CreateSequenceEnrollmentRequest(sequenceId, SequenceEnrollment.TargetType.LEAD, leadId, otherUserId)))
                .isInstanceOf(ForbiddenException.class);
        verify(sequenceEnrollmentRepository, never()).save(any());
    }

    @Test
    void create_targetLeadNotInOrganization_isNotFound() {
        UUID sequenceId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        when(sequenceService.findOrThrow(organizationId, sequenceId)).thenReturn(sequence(sequenceId));
        when(leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(leadId, organizationId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                principal(callerId), new CreateSequenceEnrollmentRequest(sequenceId, SequenceEnrollment.TargetType.LEAD, leadId, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sequenceEnrollmentRepository, never()).save(any());
    }

    @Test
    void create_duplicateActiveEnrollment_isRejected() {
        UUID sequenceId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        when(sequenceService.findOrThrow(organizationId, sequenceId)).thenReturn(sequence(sequenceId));
        when(leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(leadId, organizationId)).thenReturn(true);
        when(sequenceEnrollmentRepository.existsByOrganizationIdAndSequenceIdAndTargetTypeAndTargetIdAndDeletedAtIsNull(
                organizationId, sequenceId, SequenceEnrollment.TargetType.LEAD, leadId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                principal(callerId), new CreateSequenceEnrollmentRequest(sequenceId, SequenceEnrollment.TargetType.LEAD, leadId, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(sequenceEnrollmentRepository, never()).save(any());
    }

    @Test
    void advance_middleOfSequence_incrementsIndexAndStaysActive() {
        UUID enrollmentId = UUID.randomUUID();
        UUID sequenceId = UUID.randomUUID();
        SequenceEnrollment enrollment = enrollment(enrollmentId, sequenceId, callerId);
        when(sequenceEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)).thenReturn(Optional.of(enrollment));
        when(sequenceService.stepsOf(sequenceId)).thenReturn(List.of(step(sequenceId, 0), step(sequenceId, 1), step(sequenceId, 2)));

        SequenceEnrollment result = service.advance(principal(callerId), enrollmentId);

        assertThat(result.getCurrentStepIndex()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(SequenceEnrollment.Status.ACTIVE);
        assertThat(result.getCompletedAt()).isNull();
    }

    @Test
    void advance_pastLastStep_autoCompletes() {
        UUID enrollmentId = UUID.randomUUID();
        UUID sequenceId = UUID.randomUUID();
        SequenceEnrollment enrollment = enrollment(enrollmentId, sequenceId, callerId);
        enrollment.setCurrentStepIndex(1);
        when(sequenceEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)).thenReturn(Optional.of(enrollment));
        when(sequenceService.stepsOf(sequenceId)).thenReturn(List.of(step(sequenceId, 0), step(sequenceId, 1)));

        SequenceEnrollment result = service.advance(principal(callerId), enrollmentId);

        assertThat(result.getCurrentStepIndex()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(SequenceEnrollment.Status.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void advance_nonActiveEnrollment_isRejected() {
        UUID enrollmentId = UUID.randomUUID();
        UUID sequenceId = UUID.randomUUID();
        SequenceEnrollment enrollment = enrollment(enrollmentId, sequenceId, callerId);
        enrollment.setStatus(SequenceEnrollment.Status.PAUSED);
        when(sequenceEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.advance(principal(callerId), enrollmentId)).isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStatus_rejectsCompletedDirectly() {
        UUID enrollmentId = UUID.randomUUID();
        UUID sequenceId = UUID.randomUUID();
        SequenceEnrollment enrollment = enrollment(enrollmentId, sequenceId, callerId);
        when(sequenceEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.updateStatus(principal(callerId), enrollmentId, SequenceEnrollment.Status.COMPLETED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStatus_pausingIsAllowed() {
        UUID enrollmentId = UUID.randomUUID();
        UUID sequenceId = UUID.randomUUID();
        SequenceEnrollment enrollment = enrollment(enrollmentId, sequenceId, callerId);
        when(sequenceEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)).thenReturn(Optional.of(enrollment));

        SequenceEnrollment result = service.updateStatus(principal(callerId), enrollmentId, SequenceEnrollment.Status.PAUSED);

        assertThat(result.getStatus()).isEqualTo(SequenceEnrollment.Status.PAUSED);
    }

    private Sequence sequence(UUID id) {
        Sequence sequence = new Sequence(organizationId, "New Lead Outreach");
        sequence.setId(id);
        return sequence;
    }

    private SequenceStep step(UUID sequenceId, int order) {
        return new SequenceStep(sequenceId, order, SequenceStep.Type.EMAIL, order * 2);
    }

    private SequenceEnrollment enrollment(UUID id, UUID sequenceId, UUID ownerId) {
        SequenceEnrollment enrollment = new SequenceEnrollment(
                organizationId, sequenceId, SequenceEnrollment.TargetType.LEAD, UUID.randomUUID(), ownerId);
        enrollment.setId(id);
        return enrollment;
    }
}
