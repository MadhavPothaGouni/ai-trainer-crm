package com.aitrainercrm.platform.room.dto;

import com.aitrainercrm.platform.room.entity.Room;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoomRequest(
        @NotBlank @Size(max = 50) String label,
        @Size(max = 200) String location,
        @Min(1) Integer capacity,
        @NotNull Room.Status status,
        @Size(max = 2000) String notes) {
}
