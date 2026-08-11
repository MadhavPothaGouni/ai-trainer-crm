package com.aitrainercrm.platform.order.dto;

import com.aitrainercrm.platform.order.entity.Order;
import jakarta.validation.constraints.NotNull;

/** Drives OrderService#updateStatus - FULFILLED and CANCELLED only; DRAFT -&gt; CONFIRMED goes through the separate {@code POST /orders/{id}/confirm} (ORDER:APPROVE) endpoint instead. */
public record UpdateOrderStatusRequest(@NotNull Order.Status status) {
}
