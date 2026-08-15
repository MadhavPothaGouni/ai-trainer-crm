package com.aitrainercrm.platform.groupclass.dto;

import jakarta.validation.constraints.Size;

public record UpdateClassAttendanceRequest(@Size(max = 500) String notes) {
}
