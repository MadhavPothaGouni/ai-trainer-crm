package com.aitrainercrm.platform.vendor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Status is deliberately not editable here - see UpdatePurchaseOrderStatusRequest / PATCH .../status, same reasoning UpdateShiftStatusRequest documents. */
public record UpdatePurchaseOrderRequest(
        @NotNull LocalDate orderDate,
        @DecimalMin(value = "0", inclusive = true) BigDecimal totalAmount,
        LocalDate expectedDeliveryDate,
        @Size(max = 2000) String notes) {
}
