package com.aitrainercrm.platform.groupclass.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateClassAttendanceRequest(@NotNull UUID classSessionId, @NotNull UUID contactId, @Size(max = 500) String notes) {
}
