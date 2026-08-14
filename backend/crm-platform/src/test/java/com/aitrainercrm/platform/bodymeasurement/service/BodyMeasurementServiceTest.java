package com.aitrainercrm.platform.bodymeasurement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.bodymeasurement.dto.CreateBodyMeasurementRequest;
import com.aitrainercrm.platform.bodymeasurement.dto.UpdateBodyMeasurementRequest;
import com.aitrainercrm.platform.bodymeasurement.entity.BodyMeasurement;
import com.aitrainercrm.platform.bodymeasurement.repository.BodyMeasurementRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
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

/** See {@link BodyMeasurementService}'s javadoc for the shape this mirrors ({@code NutritionPlanService}/{@code ClientGoalService}). */
@ExtendWith(MockitoExtension.class)
class BodyMeasurementServiceTest {

    @Mock private BodyMeasurementRepository bodyMeasurementRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private BodyMeasurementService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BodyMeasurementService(bodyMeasurementRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "coach@example.com", organizationId, List.of());
    }

    private CreateBodyMeasurementRequest createRequest(UUID ownerId) {
        return new CreateBodyMeasurementRequest(
                contactId, LocalDate.of(2027, 1, 15), new BigDecimal("182.50"), "lbs",
                new BigDecimal("18.20"), new BigDecimal("101.00"), new BigDecimal("88.50"), new BigDecimal("102.00"),
                "First check-in", ownerId);
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        BodyMeasurement result = service.create(principal(callerId), createRequest(null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getContactId()).isEqualTo(contactId);
        assertThat(result.getMeasuredAt()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(result.getWeightValue()).isEqualByComparingTo("182.50");
        verify(bodyMeasurementRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(otherUserId)))
                .isInstanceOf(ForbiddenException.class);
        verify(bodyMeasurementRepository, never()).save(any());
    }

    @Test
    void create_contactNotInOrganization_throwsNotFound() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bodyMeasurementRepository, never()).save(any());
    }

    @Test
    void update_changesFieldsAndReturnsTheSavedEntity() {
        UUID measurementId = UUID.randomUUID();
        BodyMeasurement measurement = new BodyMeasurement(organizationId, contactId, callerId, LocalDate.of(2027, 1, 1));
        measurement.setId(measurementId);
        when(bodyMeasurementRepository.findActiveByIdAndOrganizationId(measurementId, organizationId)).thenReturn(Optional.of(measurement));

        UpdateBodyMeasurementRequest request = new UpdateBodyMeasurementRequest(
                LocalDate.of(2027, 1, 22), new BigDecimal("180.00"), "lbs", new BigDecimal("17.50"),
                new BigDecimal("100.00"), new BigDecimal("87.00"), new BigDecimal("101.00"), "Week 2");

        BodyMeasurement result = service.update(principal(callerId), measurementId, request);

        assertThat(result.getMeasuredAt()).isEqualTo(LocalDate.of(2027, 1, 22));
        assertThat(result.getWeightValue()).isEqualByComparingTo("180.00");
        assertThat(result.getNotes()).isEqualTo("Week 2");
        verify(bodyMeasurementRepository).save(result);
    }

    @Test
    void delete_softDeletesTheRecord() {
        UUID measurementId = UUID.randomUUID();
        BodyMeasurement measurement = new BodyMeasurement(organizationId, contactId, callerId, LocalDate.of(2027, 1, 1));
        measurement.setId(measurementId);
        when(bodyMeasurementRepository.findActiveByIdAndOrganizationId(measurementId, organizationId)).thenReturn(Optional.of(measurement));

        service.delete(principal(callerId), measurementId);

        assertThat(measurement.isDeleted()).isTrue();
        verify(bodyMeasurementRepository).save(measurement);
    }
}
