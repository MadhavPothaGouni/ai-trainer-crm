package com.aitrainercrm.platform.calendar.dto;

import java.util.UUID;

/** Exactly one of userId/externalEmail must be set - validated by CalendarEventService#addAttendee (and, as a last line of defense, the DB check constraint in V15) - same pattern as AddCampaignMemberRequest. */
public record AddAttendeeRequest(UUID userId, String externalEmail) {
}
