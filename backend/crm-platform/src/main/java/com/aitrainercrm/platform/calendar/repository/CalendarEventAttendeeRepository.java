package com.aitrainercrm.platform.calendar.repository;

import com.aitrainercrm.platform.calendar.entity.CalendarEventAttendee;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventAttendeeRepository extends JpaRepository<CalendarEventAttendee, UUID> {

    List<CalendarEventAttendee> findByCalendarEventIdOrderByCreatedAtAsc(UUID calendarEventId);

    Optional<CalendarEventAttendee> findByIdAndCalendarEventId(UUID id, UUID calendarEventId);

    boolean existsByCalendarEventIdAndUserId(UUID calendarEventId, UUID userId);

    boolean existsByCalendarEventIdAndExternalEmail(UUID calendarEventId, String externalEmail);
}
