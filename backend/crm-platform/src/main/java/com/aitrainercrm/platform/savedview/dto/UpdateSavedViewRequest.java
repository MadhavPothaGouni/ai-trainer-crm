package com.aitrainercrm.platform.savedview.dto;

import com.aitrainercrm.platform.savedview.entity.SavedView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** entityType is not editable here - same "retire and recreate rather than repurpose" reasoning UpdateTerritoryRuleRequest documents for targetResource. isDefault is deliberately absent too - see SavedViewService#setDefault, a dedicated endpoint, same shape Dashboard uses. */
public record UpdateSavedViewRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank String filters,
        @Size(max = 50) String sortField,
        SavedView.SortDirection sortDirection) {
}
