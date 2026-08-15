package com.aitrainercrm.platform.nutritionlog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.nutritionlog.dto.CreateNutritionLogRequest;
import com.aitrainercrm.platform.nutritionlog.entity.NutritionLog;
import com.aitrainercrm.platform.nutritionlog.repository.NutritionLogRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link NutritionLogService}'s javadoc - mostly exists to cover the resolveOwner self-vs-other split, same as every owner-scoped sibling. */
@ExtendWith(MockitoExtension.class)
class NutritionLogServiceTest {

    @Mock private NutritionLogRepository nutritionLogRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private NutritionLogService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NutritionLogService(nutritionLogRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "coach@example.com", organizationId, List.of());
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        NutritionLog log = service.create(
                principal(), new CreateNutritionLogRequest(contactId, Instant.now(), NutritionLog.MealType.LUNCH, 600, null, null, null, null, null));

        assertThat(log.getOwnerId()).isEqualTo(callerId);
        assertThat(log.getMealType()).isEqualTo(NutritionLog.MealType.LUNCH);
        assertThat(log.getCalories()).isEqualTo(600);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(UserPrincipal.class), eq(Permission.Resource.NUTRITION_LOG), eq(Permission.Action.CREATE)))
                .thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(
                        principal(),
                        new CreateNutritionLogRequest(
                                contactId, Instant.now(), NutritionLog.MealType.LUNCH, 600, null, null, null, null, otherUserId)))
                .isInstanceOf(ForbiddenException.class);
    }
}
