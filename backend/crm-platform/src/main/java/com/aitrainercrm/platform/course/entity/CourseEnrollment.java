package com.aitrainercrm.platform.course.entity;

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
 * One learner's progress through one {@link Course} - a normal owner-scoped CRM record, the same
 * shape {@code Ticket}/{@code Quote}/{@code Activity} already use, except {@link #userId} (the
 * enrolled learner) plays the role every other module calls {@code ownerId}; see V31's migration
 * comment for why the column is named for what it means here rather than reused verbatim. {@code
 * ScopeAuthorizationService#assertCanAccess} is always called with {@link #userId} as the record
 * owner - a rep can always see/update their own enrollments (OWN scope), a manager their team's
 * (TEAM), and so on up the ladder, exactly like every other {@code isCoreCrmResource} entity.
 *
 * <p>{@link #assignedByUserId} is purely informational (like {@code Notification#senderUserId} -
 * see its own javadoc for the same "grants no access of its own" note): null means the learner
 * self-enrolled, non-null names whoever assigned it, but {@code CourseEnrollmentService} never
 * checks it for authorization.
 */
@Entity
@Table(name = "course_enrollments")
@Getter
@Setter
@NoArgsConstructor
public class CourseEnrollment extends BaseEntity {

    public enum Status {
        NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assigned_by_user_id")
    private UUID assignedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NOT_STARTED;

    @Column(name = "score_percent")
    private Integer scorePercent;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public CourseEnrollment(UUID organizationId, UUID courseId, UUID userId) {
        this.organizationId = organizationId;
        this.courseId = courseId;
        this.userId = userId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
