package com.aitrainercrm.platform.savedview.dto;

import com.aitrainercrm.platform.savedview.entity.SavedView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** ownerUserId is deliberately not a field here - SavedViewService#create always derives it from the caller, the one place this module diverges from CreateNotificationRequest (whose recipientUserId IS caller-supplied). */
public record CreateSavedViewRequest(
        @NotNull SavedView.EntityType entityType,
        @NotBlank @Size(max = 150) String name,
        @NotBlank String filters,
        @Size(max = 50) String sortField,
        SavedView.SortDirection sortDirection) {
}
