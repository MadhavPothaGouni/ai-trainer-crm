package com.aitrainercrm.platform.booking.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** endAt is computed by BookingLinkService from the link's current durationMinutes, then stored - see BookingSlot's javadoc. */
public record CreateBookingSlotRequest(@NotNull Instant startAt) {
}
