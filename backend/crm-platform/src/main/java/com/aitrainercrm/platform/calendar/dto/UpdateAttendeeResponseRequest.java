package com.aitrainercrm.platform.calendar.dto;

import com.aitrainercrm.platform.calendar.entity.CalendarEventAttendee;
import jakarta.validation.constraints.NotNull;

public record UpdateAttendeeResponseRequest(@NotNull CalendarEventAttendee.ResponseStatus responseStatus) {
}
