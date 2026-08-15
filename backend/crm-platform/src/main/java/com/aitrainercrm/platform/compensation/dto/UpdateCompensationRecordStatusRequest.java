package com.aitrainercrm.platform.compensation.dto;

import com.aitrainercrm.platform.compensation.entity.CompensationRecord;
import jakarta.validation.constraints.NotNull;

public record UpdateCompensationRecordStatusRequest(@NotNull CompensationRecord.Status status) {
}
