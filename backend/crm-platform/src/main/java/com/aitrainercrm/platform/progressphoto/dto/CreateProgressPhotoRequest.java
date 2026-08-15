package com.aitrainercrm.platform.progressphoto.dto;

import com.aitrainercrm.platform.progressphoto.entity.ProgressPhoto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateProgressPhotoRequest(
        @NotNull UUID contactId,
        @NotBlank @Size(max = 1000) String photoUrl,
        @NotNull ProgressPhoto.Category category,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
