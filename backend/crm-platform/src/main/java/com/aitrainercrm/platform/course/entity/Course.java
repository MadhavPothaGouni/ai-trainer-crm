package com.aitrainercrm.platform.course.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A catalog entry in the org's training library - see V31's migration comment for why this mirrors
 * {@code product.entity.Product} rather than an owner-scoped entity: a course is shared organization
 * data an admin maintains, not something one rep owns, so there's no {@code ownerId} column and
 * {@code CourseService} does no {@code ScopeAuthorizationService} record-level check - holding any of
 * COURSE's three seeded scopes (TEAM/DEPARTMENT/ORGANIZATION, no OWN) grants the action against every
 * course in the org.
 *
 * <p>{@link #passingScorePercent} is read by {@code CourseEnrollmentService#complete} to decide
 * whether a submitted {@code scorePercent} lands an enrollment in {@code COMPLETED} or {@code
 * FAILED} - see that method's javadoc.
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
public class Course extends BaseEntity {

    public enum Category {
        SALES, PRODUCT, COMPLIANCE, ONBOARDING, LEADERSHIP, TECHNICAL
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "passing_score_percent", nullable = false)
    private int passingScorePercent = 70;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Course(UUID organizationId, String title, Category category) {
        this.organizationId = organizationId;
        this.title = title;
        this.category = category;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
