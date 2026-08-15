package com.aitrainercrm.platform.equipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.equipment.dto.CreateMaintenanceLogRequest;
import com.aitrainercrm.platform.equipment.entity.Equipment;
import com.aitrainercrm.platform.equipment.entity.MaintenanceLog;
import com.aitrainercrm.platform.equipment.repository.MaintenanceLogRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link MaintenanceLogService}'s javadoc for the shape this mirrors (MembershipService/ClassSessionService, minus a status field). */
@ExtendWith(MockitoExtension.class)
class MaintenanceLogServiceTest {

    @Mock private MaintenanceLogRepository maintenanceLogRepository;
    @Mock private EquipmentService equipmentService;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private MaintenanceLogService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID equipmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MaintenanceLogService(maintenanceLogRepository, equipmentService, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "tech@example.com", organizationId, List.of());
    }

    private CreateMaintenanceLogRequest createRequest(UUID ownerId) {
        return new CreateMaintenanceLogRequest(equipmentId, Instant.parse("2026-02-01T09:00:00Z"), MaintenanceLog.Type.REPAIR, new BigDecimal("150.00"), "Replaced belt", null, ownerId);
    }

    @Test
    void create_defaultsOwnerToTheCallerAndValidatesTheParentEquipment() {
        when(equipmentService.findOrThrow(organizationId, equipmentId)).thenReturn(new Equipment(organizationId, "Treadmill #3"));

        MaintenanceLog result = service.create(principal(callerId), createRequest(null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(result.getType()).isEqualTo(MaintenanceLog.Type.REPAIR);
        verify(maintenanceLogRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(otherUserId))).isInstanceOf(ForbiddenException.class);
        verify(maintenanceLogRepository, never()).save(any());
    }
}
