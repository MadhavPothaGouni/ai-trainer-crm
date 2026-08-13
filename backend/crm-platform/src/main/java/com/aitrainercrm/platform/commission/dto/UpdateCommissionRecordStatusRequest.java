package com.aitrainercrm.platform.commission.dto;

import com.aitrainercrm.platform.commission.entity.CommissionRecord;
import jakarta.validation.constraints.NotNull;

/** Only PENDING -> APPROVED and APPROVED -> PAID are legal transitions - CommissionRecordService#
 * updateStatus rejects anything else (including going backward), the same one-way-state-machine
 * shape Lead/Order's status fields already use elsewhere in this codebase. */
public record UpdateCommissionRecordStatusRequest(@NotNull CommissionRecord.Status status) {
}
