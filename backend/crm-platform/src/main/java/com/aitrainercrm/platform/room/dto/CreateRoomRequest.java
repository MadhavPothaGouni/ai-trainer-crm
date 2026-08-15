package com.aitrainercrm.platform.room.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 50) String label,
        @Size(max = 200) String location,
        @Min(1) Integer capacity,
        @Size(max = 2000) String notes) {
}
