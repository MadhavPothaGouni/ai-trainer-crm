package com.aitrainercrm.platform.vendor.dto;

import com.aitrainercrm.platform.vendor.entity.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseOrderDto(
        UUID id,
        UUID vendorId,
        UUID ownerId,
        LocalDate orderDate,
        PurchaseOrder.Status status,
        BigDecimal totalAmount,
        LocalDate expectedDeliveryDate,
        Instant receivedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static PurchaseOrderDto from(PurchaseOrder order) {
        return new PurchaseOrderDto(
                order.getId(),
                order.getVendorId(),
                order.getOwnerId(),
                order.getOrderDate(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getExpectedDeliveryDate(),
                order.getReceivedAt(),
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
