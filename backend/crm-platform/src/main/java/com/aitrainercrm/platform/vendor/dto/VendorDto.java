package com.aitrainercrm.platform.vendor.dto;

import com.aitrainercrm.platform.vendor.entity.Vendor;
import java.time.Instant;
import java.util.UUID;

public record VendorDto(
        UUID id,
        String name,
        String contactName,
        String email,
        String phone,
        String category,
        boolean active,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static VendorDto from(Vendor vendor) {
        return new VendorDto(
                vendor.getId(),
                vendor.getName(),
                vendor.getContactName(),
                vendor.getEmail(),
                vendor.getPhone(),
                vendor.getCategory(),
                vendor.isActive(),
                vendor.getNotes(),
                vendor.getCreatedAt(),
                vendor.getUpdatedAt());
    }
}
