package com.aitrainercrm.platform.locker.dto;

import com.aitrainercrm.platform.locker.entity.LockerAssignment;
import jakarta.validation.constraints.NotNull;

public record UpdateLockerAssignmentStatusRequest(@NotNull LockerAssignment.Status status) {
}
