package com.aitrainercrm.platform.groupclass.dto;

import com.aitrainercrm.platform.groupclass.entity.ClassWaitlist;
import java.time.Instant;
import java.util.UUID;

public record ClassWaitlistDto(
        UUID id,
        UUID classSessionId,
        UUID contactId,
        UUID ownerId,
        int position,
        ClassWaitlist.Status status,
        Instant notifiedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ClassWaitlistDto from(ClassWaitlist waitlist) {
        return new ClassWaitlistDto(
                waitlist.getId(),
                waitlist.getClassSessionId(),
                waitlist.getContactId(),
                waitlist.getOwnerId(),
                waitlist.getPosition(),
                waitlist.getStatus(),
                waitlist.getNotifiedAt(),
                waitlist.getNotes(),
                waitlist.getCreatedAt(),
                waitlist.getUpdatedAt());
    }
}
