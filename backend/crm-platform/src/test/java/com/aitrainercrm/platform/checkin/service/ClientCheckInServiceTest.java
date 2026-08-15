package com.aitrainercrm.platform.checkin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.checkin.dto.CreateClientCheckInRequest;
import com.aitrainercrm.platform.checkin.entity.ClientCheckIn;
import com.aitrainercrm.platform.checkin.repository.ClientCheckInRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
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

/** See {@link ClientCheckInService}'s javadoc for the shape this mirrors ({@code TimeOffRequestService}). */
@ExtendWith(MockitoExtension.class)
class ClientCheckInServiceTest {

    @Mock private ClientCheckInRepository clientCheckInRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ClientCheckInService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClientCheckInService(clientCheckInRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "frontdesk@example.com", organizationId, List.of());
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        CreateClientCheckInRequest request = new CreateClientCheckInRequest(contactId, ClientCheckIn.Method.KIOSK, null, null);

        ClientCheckIn result = service.create(principal(callerId), request);

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getContactId()).isEqualTo(contactId);
        assertThat(result.getStatus()).isEqualTo(ClientCheckIn.Status.CHECKED_IN);
        verify(clientCheckInRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);
        CreateClientCheckInRequest request = new CreateClientCheckInRequest(contactId, ClientCheckIn.Method.MANUAL, null, otherUserId);

        assertThatThrownBy(() -> service.create(principal(callerId), request)).isInstanceOf(ForbiddenException.class);
        verify(clientCheckInRepository, never()).save(any());
    }

    @Test
    void updateStatus_movingToCheckedOutThenBackAndForth_stampsCheckedOutAtOnlyOnce() {
        UUID checkInId = UUID.randomUUID();
        ClientCheckIn checkIn = new ClientCheckIn(organizationId, contactId, callerId);
        checkIn.setId(checkInId);
        when(clientCheckInRepository.findActiveByIdAndOrganizationId(checkInId, organizationId)).thenReturn(Optional.of(checkIn));

        ClientCheckIn checkedOut = service.updateStatus(principal(callerId), checkInId, ClientCheckIn.Status.CHECKED_OUT);
        Instant checkedOutAt = checkedOut.getCheckedOutAt();
        assertThat(checkedOutAt).isNotNull();

        ClientCheckIn backToCheckedIn = service.updateStatus(principal(callerId), checkInId, ClientCheckIn.Status.CHECKED_IN);
        assertThat(backToCheckedIn.getCheckedOutAt()).isEqualTo(checkedOutAt);

        ClientCheckIn checkedOutAgain = service.updateStatus(principal(callerId), checkInId, ClientCheckIn.Status.CHECKED_OUT);
        assertThat(checkedOutAgain.getCheckedOutAt()).isEqualTo(checkedOutAt);
    }
}
