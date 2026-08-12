package com.aitrainercrm.platform.savedview.dto;

import com.aitrainercrm.platform.savedview.entity.SavedView;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record SavedViewDto(
        UUID id,
        SavedView.EntityType entityType,
        String name,
        String filters,
        String sortField,
        SavedView.SortDirection sortDirection,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt) {

    public static SavedViewDto from(SavedView view) {
        return SavedViewDto.builder()
                .id(view.getId())
                .entityType(view.getEntityType())
                .name(view.getName())
                .filters(view.getFilters())
                .sortField(view.getSortField())
                .sortDirection(view.getSortDirection())
                .isDefault(view.isDefaultView())
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .build();
    }
}
