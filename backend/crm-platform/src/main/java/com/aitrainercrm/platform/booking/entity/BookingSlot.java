package com.aitrainercrm.platform.booking.entity;

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
 * One open (or booked, or cancelled) time slot on a {@link BookingLink} - see V33's migration
 * comment for the full state machine and the "snapshot, don't drift" reasoning behind {@link
 * #endAt} being stored rather than derived live from the link's current {@code durationMinutes}.
 * {@link #targetType}/{@link #targetId} and {@link #calendarEventId} stay null until {@link
 * BookingLink} is actually booked (they're never re-nulled on cancellation - see {@code
 * BookingLinkService#cancel}'s javadoc for why history survives a cancellation).
 */
@Entity
@Table(name = "booking_slots")
@Getter
@Setter
@NoArgsConstructor
public class BookingSlot extends BaseEntity {

    public enum Status {
        OPEN, BOOKED, CANCELLED
    }

    public enum TargetType {
        LEAD, CONTACT
    }

    @Column(name = "booking_link_id", nullable = false)
    private UUID bookingLinkId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private TargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "booked_at")
    private Instant bookedAt;

    @Column(name = "calendar_event_id")
    private UUID calendarEventId;

    public BookingSlot(UUID bookingLinkId, Instant startAt, Instant endAt) {
        this.bookingLinkId = bookingLinkId;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }
}
