package com.aitrainercrm.platform.booking.dto;

import com.aitrainercrm.platform.booking.entity.BookingSlot;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BookSlotRequest(@NotNull BookingSlot.TargetType targetType, @NotNull UUID targetId) {
}
