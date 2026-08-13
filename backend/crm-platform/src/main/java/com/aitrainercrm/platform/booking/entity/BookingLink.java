package com.aitrainercrm.platform.booking.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reusable "book time with me" link a rep publishes - see V33's migration comment for the module
 * overview. Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION shape, same as {@link
 * com.aitrainercrm.platform.ticket.entity.Ticket}; the actual open time slots live in {@link
 * BookingSlot}, a real FK child row {@code BookingLinkService} manages the same way {@code
 * QuoteService} manages line items.
 */
@Entity
@Table(name = "booking_links")
@Getter
@Setter
@NoArgsConstructor
public class BookingLink extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public BookingLink(UUID organizationId, UUID ownerId, String title, int durationMinutes, String slug) {
        this.organizationId = organizationId;
        this.ownerId = ownerId;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.slug = slug;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
