package com.aitrainercrm.platform.quote.dto;

import com.aitrainercrm.platform.quote.entity.Quote;
import jakarta.validation.constraints.NotNull;

public record UpdateQuoteStatusRequest(@NotNull Quote.Status status) {
}
