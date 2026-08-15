package com.aitrainercrm.platform.groupclass.dto;

import com.aitrainercrm.platform.groupclass.entity.ClassAttendance;
import jakarta.validation.constraints.NotNull;

public record UpdateClassAttendanceStatusRequest(@NotNull ClassAttendance.Status status) {
}
