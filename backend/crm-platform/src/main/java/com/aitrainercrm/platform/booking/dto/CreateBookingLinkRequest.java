package com.aitrainercrm.platform.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateBookingLinkRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Min(1) int durationMinutes,

        /** URL-safe slug, unique per organization - see uq_booking_links_org_slug (V33). */
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Use lowercase letters, numbers, and hyphens only") String slug,

        /** Null defaults to the creator - see BookingLinkService#resolveOwner's javadoc. */
        UUID ownerId) {
}
