package com.aitrainercrm.platform.activity.dto;

import com.aitrainercrm.platform.activity.entity.Activity;
import jakarta.validation.constraints.NotNull;

/** Marks an activity OPEN (reopen) or COMPLETED (done) - ActivityService stamps/clears completedAt to match. */
public record UpdateActivityStatusRequest(@NotNull Activity.Status status) {
}
