package com.aitrainercrm.platform.timeoff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.timeoff.dto.CreateTimeOffRequestRequest;
import com.aitrainercrm.platform.timeoff.entity.TimeOffRequest;
import com.aitrainercrm.platform.timeoff.repository.TimeOffRequestRepository;
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

/** See {@link TimeOffRequestService}'s javadoc for the shape this mirrors ({@code ReferralServiceTest}). */
@ExtendWith(MockitoExtension.class)
class TimeOffRequestServiceTest {

    @Mock private TimeOffRequestRepository timeOffRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private TimeOffRequestService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TimeOffRequestService(timeOffRequestRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "coach@example.com", organizationId, List.of());
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        CreateTimeOffRequestRequest request = new CreateTimeOffRequestRequest(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), TimeOffRequest.Type.VACATION, "Family trip", null, null);

        TimeOffRequest result = service.create(principal(callerId), request);

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getStatus()).isEqualTo(TimeOffRequest.Status.PENDING);
        assertThat(result.getType()).isEqualTo(TimeOffRequest.Type.VACATION);
        verify(timeOffRequestRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);
        CreateTimeOffRequestRequest request = new CreateTimeOffRequestRequest(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), TimeOffRequest.Type.VACATION, null, null, otherUserId);

        assertThatThrownBy(() -> service.create(principal(callerId), request)).isInstanceOf(ForbiddenException.class);
        verify(timeOffRequestRepository, never()).save(any());
    }

    @Test
    void updateStatus_movingToApproved_stampsApprovedAtOnlyOnce() {
        UUID timeOffRequestId = UUID.randomUUID();
        TimeOffRequest timeOffRequest =
                new TimeOffRequest(organizationId, callerId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        timeOffRequest.setId(timeOffRequestId);
        when(timeOffRequestRepository.findActiveByIdAndOrganizationId(timeOffRequestId, organizationId))
                .thenReturn(Optional.of(timeOffRequest));

        TimeOffRequest firstApproval = service.updateStatus(principal(callerId), timeOffRequestId, TimeOffRequest.Status.APPROVED);
        Instant firstApprovedAt = firstApproval.getApprovedAt();
        assertThat(firstApprovedAt).isNotNull();

        // Moving away and back to APPROVED must not restamp approvedAt.
        service.updateStatus(principal(callerId), timeOffRequestId, TimeOffRequest.Status.DENIED);
        TimeOffRequest secondApproval = service.updateStatus(principal(callerId), timeOffRequestId, TimeOffRequest.Status.APPROVED);
        assertThat(secondApproval.getApprovedAt()).isEqualTo(firstApprovedAt);
    }

    @Test
    void delete_stampsDeletedAt() {
        UUID timeOffRequestId = UUID.randomUUID();
        TimeOffRequest timeOffRequest =
                new TimeOffRequest(organizationId, callerId, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
        timeOffRequest.setId(timeOffRequestId);
        when(timeOffRequestRepository.findActiveByIdAndOrganizationId(timeOffRequestId, organizationId))
                .thenReturn(Optional.of(timeOffRequest));

        service.delete(principal(callerId), timeOffRequestId);

        assertThat(timeOffRequest.getDeletedAt()).isNotNull();
        verify(timeOffRequestRepository).save(timeOffRequest);
    }
}
