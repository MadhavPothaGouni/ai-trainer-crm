package com.aitrainercrm.platform.calendar.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One attendee of a {@link CalendarEvent} - exactly one of {@link #userId}/
 * {@link #externalEmail} is ever set (enforced both by a DB check constraint
 * in V15 and by {@code CalendarEventService#addAttendee}), same shape as
 * {@code CampaignMember}'s leadId/contactId exclusivity. There's no {@code
 * organizationId} here directly - membership is scoped through {@link
 * #calendarEventId}, same as {@code CampaignMember} has no organizationId
 * of its own.
 */
@Entity
@Table(name = "calendar_event_attendees")
@Getter
@Setter
@NoArgsConstructor
public class CalendarEventAttendee extends BaseEntity {

    public enum ResponseStatus {
        NEEDS_ACTION, ACCEPTED, DECLINED, TENTATIVE
    }

    @Column(name = "calendar_event_id", nullable = false)
    private UUID calendarEventId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "external_email")
    private String externalEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false, length = 20)
    private ResponseStatus responseStatus = ResponseStatus.NEEDS_ACTION;

    public CalendarEventAttendee(UUID calendarEventId, UUID userId, String externalEmail) {
        this.calendarEventId = calendarEventId;
        this.userId = userId;
        this.externalEmail = externalEmail;
    }
}
