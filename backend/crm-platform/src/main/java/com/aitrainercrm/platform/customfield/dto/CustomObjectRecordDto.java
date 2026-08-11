package com.aitrainercrm.platform.customfield.dto;

import com.aitrainercrm.platform.customfield.entity.CustomObjectRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CustomObjectRecordDto(
        UUID id, UUID customObjectId, String name, Instant createdAt, Instant updatedAt, List<CustomFieldValueDto> values) {

    public static CustomObjectRecordDto from(CustomObjectRecord record) {
        return from(record, List.of());
    }

    public static CustomObjectRecordDto from(CustomObjectRecord record, List<CustomFieldValueDto> values) {
        return CustomObjectRecordDto.builder()
                .id(record.getId())
                .customObjectId(record.getCustomObjectId())
                .name(record.getName())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .values(values)
                .build();
    }
}
