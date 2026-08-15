package com.aitrainercrm.platform.vendor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVendorRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String contactName,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 100) String category,
        @Size(max = 2000) String notes,
        boolean active) {
}
