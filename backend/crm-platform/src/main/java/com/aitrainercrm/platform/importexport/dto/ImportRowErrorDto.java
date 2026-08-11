package com.aitrainercrm.platform.importexport.dto;

import com.aitrainercrm.platform.importexport.entity.ImportRowError;
import lombok.Builder;

@Builder
public record ImportRowErrorDto(int rowNumber, String message) {

    public static ImportRowErrorDto from(ImportRowError error) {
        return ImportRowErrorDto.builder().rowNumber(error.getRowNumber()).message(error.getMessage()).build();
    }
}
