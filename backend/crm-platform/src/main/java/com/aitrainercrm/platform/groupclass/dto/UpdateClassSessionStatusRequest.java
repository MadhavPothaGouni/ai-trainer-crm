package com.aitrainercrm.platform.groupclass.dto;

import com.aitrainercrm.platform.groupclass.entity.ClassSession;
import jakarta.validation.constraints.NotNull;

public record UpdateClassSessionStatusRequest(@NotNull ClassSession.Status status) {
}
