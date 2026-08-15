package com.aitrainercrm.platform.locker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.locker.entity.LockerAssignment;
import com.aitrainercrm.platform.locker.repository.LockerAssignmentRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link LockerAssignmentService}'s javadoc for the stamp-once behavior this mostly exists to cover (mirrors {@code PurchaseOrderServiceTest}). */
@ExtendWith(MockitoExtension.class)
class LockerAssignmentServiceTest {

    @Mock private LockerAssignmentRepository lockerAssignmentRepository;
    @Mock private LockerService lockerService;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private LockerAssignmentService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID lockerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LockerAssignmentService(
                lockerAssignmentRepository, lockerService, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void updateStatus_movingToReturnedThenBackAndForth_stampsReturnedAtOnlyOnce() {
        UUID assignmentId = UUID.randomUUID();
        LockerAssignment assignment = new LockerAssignment(organizationId, lockerId, contactId, callerId);
        assignment.setId(assignmentId);
        when(lockerAssignmentRepository.findActiveByIdAndOrganizationId(assignmentId, organizationId)).thenReturn(Optional.of(assignment));

        LockerAssignment returned = service.updateStatus(principal(), assignmentId, LockerAssignment.Status.RETURNED);
        Instant returnedAt = returned.getReturnedAt();
        assertThat(returnedAt).isNotNull();

        // A correction back to ACTIVE, then re-entering RETURNED, must not move returnedAt.
        LockerAssignment backToActive = service.updateStatus(principal(), assignmentId, LockerAssignment.Status.ACTIVE);
        assertThat(backToActive.getReturnedAt()).isEqualTo(returnedAt);

        LockerAssignment returnedAgain = service.updateStatus(principal(), assignmentId, LockerAssignment.Status.RETURNED);
        assertThat(returnedAgain.getReturnedAt()).isEqualTo(returnedAt);
    }
}
