package com.aitrainercrm.platform.region.dto;

import com.aitrainercrm.platform.region.entity.Region;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RegionDto(
        UUID id, String name, UUID parentRegionId, String description, Instant createdAt, Instant updatedAt) {

    public static RegionDto from(Region region) {
        return RegionDto.builder()
                .id(region.getId())
                .name(region.getName())
                .parentRegionId(region.getParentRegionId())
                .description(region.getDescription())
                .createdAt(region.getCreatedAt())
                .updatedAt(region.getUpdatedAt())
                .build();
    }
}
