package com.aitrainercrm.platform.trainingsession.entity;

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
 * The post-session record of what actually happened in a coaching session - the resource this
 * whole module exists to fill in; see V37's migration comment for why this is deliberately
 * distinct from {@link com.aitrainercrm.platform.booking.entity.BookingSlot} (pre-session
 * scheduling) and {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal} (the long-term
 * target this session is one unit of work toward), and why this mirrors
 * {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal}'s owner-scoped shape rather
 * than inventing a new one.
 */
@Entity
@Table(name = "training_sessions")
@Getter
@Setter
@NoArgsConstructor
public class TrainingSession extends BaseEntity {

    public enum SessionType {
        IN_PERSON, VIRTUAL, GROUP
    }

    public enum Status {
        SCHEDULED, COMPLETED, CANCELLED, NO_SHOW
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The client the session was FOR - never the authorization subject, same "owner and target are different people" split ClientGoal already established. See TrainingSession's javadoc. */
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Nullable cross-reference to the BookingSlot this session originated from, if any - not a requirement, see V37's migration comment. */
    @Column(name = "booking_slot_id")
    private UUID bookingSlotId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType = SessionType.IN_PERSON;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Column(name = "focus_area", length = 200)
    private String focusArea;

    /** The client's own perceived-effort rating (Rate of Perceived Exertion, a standard 1-10 fitness-coaching scale) - nullable, typically only recorded after COMPLETED. */
    @Column(name = "client_rpe")
    private Integer clientRpe;

    @Column(name = "coach_notes", length = 2000)
    private String coachNotes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public TrainingSession(UUID organizationId, UUID contactId, UUID ownerId, Instant startedAt) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.startedAt = startedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
