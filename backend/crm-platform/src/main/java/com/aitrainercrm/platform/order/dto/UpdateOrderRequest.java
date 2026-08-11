package com.aitrainercrm.platform.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateOrderRequest(
        @NotBlank @Size(max = 50) String orderNumber,
        @Size(max = 3) String currency,
        @DecimalMin(value = "0", inclusive = true) BigDecimal discountAmount,
        @DecimalMin(value = "0", inclusive = true) BigDecimal taxAmount) {
}
