package com.aitrainercrm.platform.locker.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Status is deliberately not editable here - see UpdateLockerAssignmentStatusRequest / PATCH .../status, same reasoning UpdatePurchaseOrderRequest documents. */
public record UpdateLockerAssignmentRequest(LocalDate expiresAt, @Size(max = 2000) String notes) {
}
