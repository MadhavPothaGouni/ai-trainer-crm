package com.aitrainercrm.platform.forecast.dto;

import com.aitrainercrm.platform.forecast.entity.PipelineSnapshot;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PipelineSnapshotDto(
        UUID id, LocalDate snapshotDate, UUID ownerId, Opportunity.Stage stage, int dealCount, BigDecimal totalValue) {

    public static PipelineSnapshotDto from(PipelineSnapshot snapshot) {
        return PipelineSnapshotDto.builder()
                .id(snapshot.getId())
                .snapshotDate(snapshot.getSnapshotDate())
                .ownerId(snapshot.getOwnerId())
                .stage(snapshot.getStage())
                .dealCount(snapshot.getDealCount())
                .totalValue(snapshot.getTotalValue())
                .build();
    }
}
