package com.aitrainercrm.platform.progressphoto.dto;

import com.aitrainercrm.platform.progressphoto.entity.ProgressPhoto;
import java.time.Instant;
import java.util.UUID;

public record ProgressPhotoDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        String photoUrl,
        ProgressPhoto.Category category,
        Instant takenAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ProgressPhotoDto from(ProgressPhoto photo) {
        return new ProgressPhotoDto(
                photo.getId(),
                photo.getContactId(),
                photo.getOwnerId(),
                photo.getPhotoUrl(),
                photo.getCategory(),
                photo.getTakenAt(),
                photo.getNotes(),
                photo.getCreatedAt(),
                photo.getUpdatedAt());
    }
}
