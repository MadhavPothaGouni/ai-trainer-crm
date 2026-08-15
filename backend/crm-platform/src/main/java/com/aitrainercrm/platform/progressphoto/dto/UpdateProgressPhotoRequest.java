package com.aitrainercrm.platform.progressphoto.dto;

import com.aitrainercrm.platform.progressphoto.entity.ProgressPhoto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProgressPhotoRequest(
        @NotBlank @Size(max = 1000) String photoUrl, @NotNull ProgressPhoto.Category category, @Size(max = 2000) String notes) {
}
