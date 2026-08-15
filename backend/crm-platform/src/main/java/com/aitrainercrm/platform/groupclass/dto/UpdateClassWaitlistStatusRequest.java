package com.aitrainercrm.platform.groupclass.dto;

import com.aitrainercrm.platform.groupclass.entity.ClassWaitlist;
import jakarta.validation.constraints.NotNull;

public record UpdateClassWaitlistStatusRequest(@NotNull ClassWaitlist.Status status) {
}
