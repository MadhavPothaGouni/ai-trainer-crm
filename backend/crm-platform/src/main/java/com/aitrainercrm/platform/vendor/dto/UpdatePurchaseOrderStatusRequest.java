package com.aitrainercrm.platform.vendor.dto;

import com.aitrainercrm.platform.vendor.entity.PurchaseOrder;
import jakarta.validation.constraints.NotNull;

public record UpdatePurchaseOrderStatusRequest(@NotNull PurchaseOrder.Status status) {
}
