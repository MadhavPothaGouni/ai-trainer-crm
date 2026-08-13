package com.aitrainercrm.platform.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBookingLinkRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Min(1) int durationMinutes,
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Use lowercase letters, numbers, and hyphens only") String slug,
        boolean active) {
}
