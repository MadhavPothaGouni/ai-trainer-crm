package com.aitrainercrm.platform.campaign.dto;

import com.aitrainercrm.platform.campaign.entity.Campaign;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateCampaignRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Campaign.Type type,
        LocalDate startDate,
        LocalDate endDate,
        @DecimalMin(value = "0", inclusive = true) BigDecimal budget,
        @DecimalMin(value = "0", inclusive = true) BigDecimal actualCost,
        @Size(max = 2000) String description) {
}
