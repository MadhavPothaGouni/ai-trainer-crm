package com.aitrainercrm.platform.shift.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.shift.entity.Shift;
import com.aitrainercrm.platform.shift.repository.ShiftRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.when;

/** See {@link ShiftService}'s javadoc for the clock-in/out stamp-once behavior this mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock private ShiftRepository shiftRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ShiftService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShiftService(shiftRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void updateStatus_movingToInProgressThenCompleted_stampsBothTimesOnlyOnce() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(organizationId, callerId, LocalDate.of(2026, 2, 2), Instant.parse("2026-02-02T07:00:00Z"), Instant.parse("2026-02-02T12:00:00Z"));
        shift.setId(shiftId);
        when(shiftRepository.findActiveByIdAndOrganizationId(shiftId, organizationId)).thenReturn(Optional.of(shift));

        Shift clockedIn = service.updateStatus(principal(), shiftId, Shift.Status.IN_PROGRESS);
        Instant clockInAt = clockedIn.getClockInAt();
        assertThat(clockInAt).isNotNull();
        assertThat(clockedIn.getClockOutAt()).isNull();

        Shift clockedOut = service.updateStatus(principal(), shiftId, Shift.Status.COMPLETED);
        assertThat(clockedOut.getClockOutAt()).isNotNull();
        assertThat(clockedOut.getClockInAt()).isEqualTo(clockInAt);

        // Re-entering IN_PROGRESS (a correction) must not move the original clock-in time.
        Shift correctedBackToInProgress = service.updateStatus(principal(), shiftId, Shift.Status.IN_PROGRESS);
        assertThat(correctedBackToInProgress.getClockInAt()).isEqualTo(clockInAt);
    }
}
