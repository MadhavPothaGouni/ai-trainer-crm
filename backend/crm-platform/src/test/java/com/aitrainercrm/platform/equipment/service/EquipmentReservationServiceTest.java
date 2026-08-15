package com.aitrainercrm.platform.equipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.equipment.entity.EquipmentReservation;
import com.aitrainercrm.platform.equipment.repository.EquipmentReservationRepository;
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

/** See {@link EquipmentReservationService}'s javadoc for why this service lives in the equipment package. */
@ExtendWith(MockitoExtension.class)
class EquipmentReservationServiceTest {

    @Mock private EquipmentReservationRepository equipmentReservationRepository;
    @Mock private EquipmentService equipmentService;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private EquipmentReservationService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID equipmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EquipmentReservationService(
                equipmentReservationRepository, equipmentService, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void updateStatus_movingToCancelledThenBackToConfirmed_isAlwaysAllowed() {
        UUID reservationId = UUID.randomUUID();
        EquipmentReservation reservation = new EquipmentReservation(organizationId, equipmentId, callerId, Instant.now(), Instant.now().plusSeconds(3600));
        reservation.setId(reservationId);
        when(equipmentReservationRepository.findActiveByIdAndOrganizationId(reservationId, organizationId)).thenReturn(Optional.of(reservation));

        EquipmentReservation cancelled = service.updateStatus(principal(), reservationId, EquipmentReservation.Status.CANCELLED);
        assertThat(cancelled.getStatus()).isEqualTo(EquipmentReservation.Status.CANCELLED);

        EquipmentReservation reconfirmed = service.updateStatus(principal(), reservationId, EquipmentReservation.Status.CONFIRMED);
        assertThat(reconfirmed.getStatus()).isEqualTo(EquipmentReservation.Status.CONFIRMED);
    }
}
