package com.aitrainercrm.platform.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String industry,
        @Size(max = 255) String website,
        @Size(max = 30) String phone,
        @Size(max = 255) String billingStreet,
        @Size(max = 100) String billingCity,
        @Size(max = 100) String billingState,
        @Size(max = 20) String billingPostalCode,
        @Size(max = 100) String billingCountry,
        @DecimalMin(value = "0", inclusive = true) BigDecimal annualRevenue,
        @Min(0) Integer employeeCount,
        @Size(max = 2000) String description) {
}
