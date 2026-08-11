package com.aitrainercrm.platform.product.dto;

import com.aitrainercrm.platform.product.entity.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProductDto(
        UUID id,
        String name,
        String sku,
        String description,
        BigDecimal unitPrice,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductDto from(Product product) {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .description(product.getDescription())
                .unitPrice(product.getUnitPrice())
                .currency(product.getCurrency())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
