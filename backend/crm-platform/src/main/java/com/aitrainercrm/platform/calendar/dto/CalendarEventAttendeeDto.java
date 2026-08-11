package com.aitrainercrm.platform.calendar.dto;

import com.aitrainercrm.platform.calendar.entity.CalendarEventAttendee;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CalendarEventAttendeeDto(UUID id, UUID userId, String externalEmail, CalendarEventAttendee.ResponseStatus responseStatus) {

    public static CalendarEventAttendeeDto from(CalendarEventAttendee attendee) {
        return CalendarEventAttendeeDto.builder()
                .id(attendee.getId())
                .userId(attendee.getUserId())
                .externalEmail(attendee.getExternalEmail())
                .responseStatus(attendee.getResponseStatus())
                .build();
    }
}
