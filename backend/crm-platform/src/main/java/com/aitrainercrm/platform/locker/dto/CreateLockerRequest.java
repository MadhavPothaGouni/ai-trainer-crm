package com.aitrainercrm.platform.locker.dto;

import com.aitrainercrm.platform.locker.entity.Locker;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLockerRequest(
        @NotBlank @Size(max = 50) String label,
        @Size(max = 200) String location,
        @NotNull Locker.Size size,
        @Size(max = 2000) String notes) {
}
