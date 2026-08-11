package com.aitrainercrm.platform.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /orders/from-quote/{quoteId}} - the quote id itself comes from the path, matching QuoteController's own path-param conventions. */
public record CreateOrderFromQuoteRequest(@NotBlank @Size(max = 50) String orderNumber) {
}
