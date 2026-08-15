package com.aitrainercrm.platform.nutritionlog.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One meal a client logged, at a point in time - see V63's migration comment. Distinct from
 * {@link com.aitrainercrm.platform.nutritionplan.entity.NutritionPlan} (a coach-authored target
 * plan) - this is the client's actual intake. Owner-scoped, same "point-in-time fact" shape
 * {@code ProgressPhoto} established: no {@code status} field, {@link #loggedAt} is simply set once
 * at creation. {@link #proteinGrams}/{@link #carbGrams}/{@link #fatGrams} are independently
 * optional since not every client logs full macros.
 */
@Entity
@Table(name = "nutrition_logs")
@Getter
@Setter
@NoArgsConstructor
public class NutritionLog extends BaseEntity {

    public enum MealType {
        BREAKFAST, LUNCH, DINNER, SNACK
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType = MealType.BREAKFAST;

    private Integer calories;

    @Column(name = "protein_grams")
    private BigDecimal proteinGrams;

    @Column(name = "carb_grams")
    private BigDecimal carbGrams;

    @Column(name = "fat_grams")
    private BigDecimal fatGrams;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public NutritionLog(UUID organizationId, UUID contactId, UUID ownerId, Instant loggedAt, MealType mealType) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.loggedAt = loggedAt;
        this.mealType = mealType;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
