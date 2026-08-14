package com.aitrainercrm.platform.nutritionplan.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A coach/trainer-authored dietary prescription (daily calorie target, macro targets, and
 * freeform guidance) for one {@link com.aitrainercrm.platform.contact.entity.Contact} over a
 * date range - see V40's migration comment for why this is distinct from every existing module
 * (ClientGoal tracks a long-term measurable outcome, TrainingSession/TrainingSessionExercise log
 * completed workouts, Exercise catalogs movements - none of them cover dietary guidance) and why
 * this mirrors {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal}'s owner-scoped
 * shape rather than inventing a new one.
 */
@Entity
@Table(name = "nutrition_plans")
@Getter
@Setter
@NoArgsConstructor
public class NutritionPlan extends BaseEntity {

    public enum Status {
        DRAFT, ACTIVE, COMPLETED, ARCHIVED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The client the plan is FOR - never the authorization subject, same "owner and target are different people" split ClientGoal/TrainingSession already established. */
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "daily_calorie_target")
    private Integer dailyCalorieTarget;

    @Column(name = "protein_target_grams")
    private Integer proteinTargetGrams;

    @Column(name = "carb_target_grams")
    private Integer carbTargetGrams;

    @Column(name = "fat_target_grams")
    private Integer fatTargetGrams;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Nullable - an ongoing plan with no defined end, same shape Contract#endDate takes for auto-renewing contracts. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public NutritionPlan(UUID organizationId, UUID contactId, UUID ownerId, String title) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.title = title;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
