package com.aitrainercrm.platform.importexport.dto;

import com.aitrainercrm.platform.importexport.entity.ImportJob;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ImportJobDto(
        UUID id,
        ImportJob.EntityType entityType,
        ImportJob.Status status,
        int totalRows,
        int successCount,
        int errorCount,
        Instant createdAt,
        List<ImportRowErrorDto> errors) {

    public static ImportJobDto from(ImportJob job, List<ImportRowErrorDto> errors) {
        return ImportJobDto.builder()
                .id(job.getId())
                .entityType(job.getEntityType())
                .status(job.getStatus())
                .totalRows(job.getTotalRows())
                .successCount(job.getSuccessCount())
                .errorCount(job.getErrorCount())
                .createdAt(job.getCreatedAt())
                .errors(errors)
                .build();
    }
}
