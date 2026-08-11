package com.aitrainercrm.platform.customfield.dto;

import com.aitrainercrm.platform.customfield.entity.CustomObject;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CustomObjectDto(
        UUID id, String apiName, String label, String pluralLabel, String description, boolean active, Instant createdAt) {

    public static CustomObjectDto from(CustomObject object) {
        return CustomObjectDto.builder()
                .id(object.getId())
                .apiName(object.getApiName())
                .label(object.getLabel())
                .pluralLabel(object.getPluralLabel())
                .description(object.getDescription())
                .active(object.isActive())
                .createdAt(object.getCreatedAt())
                .build();
    }
}
