package com.aitrainercrm.platform.nutritionplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.nutritionplan.dto.CreateNutritionPlanRequest;
import com.aitrainercrm.platform.nutritionplan.entity.NutritionPlan;
import com.aitrainercrm.platform.nutritionplan.repository.NutritionPlanRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
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

/** See {@link NutritionPlanService}'s javadoc for the shape this mirrors ({@code ClientGoalService}/{@code ContractService}). */
@ExtendWith(MockitoExtension.class)
class NutritionPlanServiceTest {

    @Mock private NutritionPlanRepository nutritionPlanRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private NutritionPlanService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NutritionPlanService(nutritionPlanRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "coach@example.com", organizationId, List.of());
    }

    private CreateNutritionPlanRequest createRequest(UUID ownerId, LocalDate startDate, LocalDate endDate) {
        return new CreateNutritionPlanRequest(contactId, "Cutting phase", 2200, 180, 200, 60, startDate, endDate, "Kickoff done", ownerId);
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        NutritionPlan result = service.create(principal(callerId), createRequest(null, null, null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getContactId()).isEqualTo(contactId);
        assertThat(result.getStatus()).isEqualTo(NutritionPlan.Status.DRAFT);
        verify(nutritionPlanRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(otherUserId, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(nutritionPlanRepository, never()).save(any());
    }

    @Test
    void create_endDateBeforeStartDate_isRejected() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(null, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 1, 1))))
                .isInstanceOf(BusinessException.class);
        verify(nutritionPlanRepository, never()).save(any());
    }

    @Test
    void create_onlyStartDateProvided_isAccepted() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        NutritionPlan result = service.create(principal(callerId), createRequest(null, LocalDate.of(2027, 1, 1), null));

        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(result.getEndDate()).isNull();
    }

    @Test
    void updateStatus_movingToArchivedAndBackToActive_isAllowed() {
        UUID planId = UUID.randomUUID();
        NutritionPlan plan = new NutritionPlan(organizationId, contactId, callerId, "Cutting phase");
        plan.setId(planId);
        when(nutritionPlanRepository.findActiveByIdAndOrganizationId(planId, organizationId)).thenReturn(Optional.of(plan));

        NutritionPlan archived = service.updateStatus(principal(callerId), planId, NutritionPlan.Status.ARCHIVED);
        assertThat(archived.getStatus()).isEqualTo(NutritionPlan.Status.ARCHIVED);

        NutritionPlan reactivated = service.updateStatus(principal(callerId), planId, NutritionPlan.Status.ACTIVE);
        assertThat(reactivated.getStatus()).isEqualTo(NutritionPlan.Status.ACTIVE);
    }
}
