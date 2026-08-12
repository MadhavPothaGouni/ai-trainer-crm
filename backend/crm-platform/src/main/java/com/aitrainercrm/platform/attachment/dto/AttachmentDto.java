package com.aitrainercrm.platform.attachment.dto;

import com.aitrainercrm.platform.attachment.entity.Attachment;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Deliberately never includes storageKey - see Attachment's javadoc for why. */
@Builder
public record AttachmentDto(
        UUID id,
        String fileName,
        String contentType,
        long fileSizeBytes,
        String description,
        Attachment.RelatedToType relatedToType,
        UUID relatedToId,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static AttachmentDto from(Attachment attachment) {
        return AttachmentDto.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSizeBytes(attachment.getFileSizeBytes())
                .description(attachment.getDescription())
                .relatedToType(attachment.getRelatedToType())
                .relatedToId(attachment.getRelatedToId())
                .ownerId(attachment.getOwnerId())
                .createdAt(attachment.getCreatedAt())
                .updatedAt(attachment.getUpdatedAt())
                .build();
    }
}
