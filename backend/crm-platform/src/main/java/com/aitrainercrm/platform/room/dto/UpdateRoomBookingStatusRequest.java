package com.aitrainercrm.platform.room.dto;

import com.aitrainercrm.platform.room.entity.RoomBooking;
import jakarta.validation.constraints.NotNull;

public record UpdateRoomBookingStatusRequest(@NotNull RoomBooking.Status status) {
}
