package com.aitrainercrm.platform.groupclass.entity;

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
 * A client queued for a spot in a full {@link ClassSession} - see V61's migration comment for the
 * backstory. Owner-scoped, same {@code contactId}-is-the-client / {@code ownerId}-is-the-
 * authorization-subject split every other contact-facing occurrence entity in this platform uses.
 * {@link #position} is computed server-side at creation and never client-settable (see
 * {@code ClassWaitlistService#create}). {@link #status} is a free state machine - moving a
 * CONVERTED entry back to WAITING is a legitimate correction, same restraint every other status
 * machine in this platform documents. {@link #notifiedAt} is stamped once, independent of later
 * status changes.
 */
@Entity
@Table(name = "class_waitlists")
@Getter
@Setter
@NoArgsConstructor
public class ClassWaitlist extends BaseEntity {

    public enum Status {
        WAITING, NOTIFIED, CONVERTED, EXPIRED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "class_session_id", nullable = false)
    private UUID classSessionId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Computed at creation - see this class's javadoc. */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.WAITING;

    /** Stamped once, the first time status moves to NOTIFIED - never overwritten afterward. */
    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ClassWaitlist(UUID organizationId, UUID classSessionId, UUID contactId, UUID ownerId, int position) {
        this.organizationId = organizationId;
        this.classSessionId = classSessionId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.position = position;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
