package com.aitrainercrm.platform.attachment.dto;

import com.aitrainercrm.platform.attachment.entity.Attachment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Metadata-only - fileName/description/relatedTo can change after upload, but not the underlying bytes (there's no re-upload endpoint; delete and re-create for that). */
public record UpdateAttachmentRequest(
        @NotBlank @Size(max = 255) String fileName,
        @Size(max = 1000) String description,
        @NotNull Attachment.RelatedToType relatedToType,
        @NotNull UUID relatedToId) {
}
