package com.aitrainercrm.platform.calendar.entity;

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
 * A scheduled meeting or block of time, optionally tied to an Account/
 * Contact/Opportunity/Lead/Ticket - see V15's migration comment for why
 * {@link #relatedToType}/{@link #relatedToId} are nullable here but not on
 * {@link com.aitrainercrm.platform.email.entity.EmailMessage}: an internal
 * team meeting has no CRM record to attach to, an email always does.
 *
 * <p>Owner-scoped like {@code Ticket}/{@code EmailMessage} - {@link
 * #ownerId} is the organizer. Attendees are a separate child entity ({@link
 * CalendarEventAttendee}), same "real child table, not a comma-separated
 * column" reasoning V15's migration comment gives for why attendees differ
 * from email's to/cc addresses.
 */
@Entity
@Table(name = "calendar_events")
@Getter
@Setter
@NoArgsConstructor
public class CalendarEvent extends BaseEntity {

    public enum RelatedToType {
        ACCOUNT, CONTACT, OPPORTUNITY, LEAD, TICKET
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 255)
    private String location;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_to_type", length = 20)
    private RelatedToType relatedToType;

    @Column(name = "related_to_id")
    private UUID relatedToId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public CalendarEvent(UUID organizationId, String title, Instant startAt, Instant endAt, UUID ownerId) {
        this.organizationId = organizationId;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
