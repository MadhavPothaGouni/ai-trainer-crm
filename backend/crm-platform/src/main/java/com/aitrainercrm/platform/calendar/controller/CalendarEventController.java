package com.aitrainercrm.platform.calendar.controller;

import com.aitrainercrm.platform.calendar.dto.AddAttendeeRequest;
import com.aitrainercrm.platform.calendar.dto.CalendarEventAttendeeDto;
import com.aitrainercrm.platform.calendar.dto.CalendarEventDto;
import com.aitrainercrm.platform.calendar.dto.CreateCalendarEventRequest;
import com.aitrainercrm.platform.calendar.dto.UpdateAttendeeResponseRequest;
import com.aitrainercrm.platform.calendar.dto.UpdateCalendarEventRequest;
import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import com.aitrainercrm.platform.calendar.service.CalendarEventService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors EmailMessageController's shape, plus a CampaignController-style attendees sub-resource gated on the parent event's own UPDATE permission - see CalendarEventService's javadoc. */
@RestController
@RequestMapping("/api/v1/calendar-events")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:READ:OWN','CALENDAR_EVENT:READ:TEAM','CALENDAR_EVENT:READ:DEPARTMENT','CALENDAR_EVENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CalendarEventDto>> list(
            @RequestParam(required = false) CalendarEvent.RelatedToType relatedToType,
            @RequestParam(required = false) UUID relatedToId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<CalendarEvent> page = calendarEventService.list(principal, relatedToType, relatedToId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CalendarEventDto::from).toList()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:EXPORT:OWN','CALENDAR_EVENT:EXPORT:TEAM','CALENDAR_EVENT:EXPORT:DEPARTMENT','CALENDAR_EVENT:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal principal) {
        byte[] csv = calendarEventService.exportCsv(principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("calendar-events.csv").build().toString())
                .body(csv);
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:READ:OWN','CALENDAR_EVENT:READ:TEAM','CALENDAR_EVENT:READ:DEPARTMENT','CALENDAR_EVENT:READ:ORGANIZATION')")
    public ApiResponse<CalendarEventDto> get(@PathVariable UUID eventId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CalendarEventDto.from(calendarEventService.get(principal, eventId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:CREATE:OWN','CALENDAR_EVENT:CREATE:TEAM','CALENDAR_EVENT:CREATE:DEPARTMENT','CALENDAR_EVENT:CREATE:ORGANIZATION')")
    public ApiResponse<CalendarEventDto> create(
            @Valid @RequestBody CreateCalendarEventRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CalendarEventDto.from(calendarEventService.create(principal, request)), "Event created");
    }

    @PutMapping("/{eventId}")
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:UPDATE:OWN','CALENDAR_EVENT:UPDATE:TEAM','CALENDAR_EVENT:UPDATE:DEPARTMENT','CALENDAR_EVENT:UPDATE:ORGANIZATION')")
    public ApiResponse<CalendarEventDto> update(
            @PathVariable UUID eventId, @Valid @RequestBody UpdateCalendarEventRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CalendarEventDto.from(calendarEventService.update(principal, eventId, request)), "Event updated");
    }

    @DeleteMapping("/{eventId}")
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:DELETE:OWN','CALENDAR_EVENT:DELETE:TEAM','CALENDAR_EVENT:DELETE:DEPARTMENT','CALENDAR_EVENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID eventId, @AuthenticationPrincipal UserPrincipal principal) {
        calendarEventService.delete(principal, eventId);
        return ApiResponse.ok(null, "Event deleted");
    }

    @PatchMapping("/{eventId}/owner")
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:ASSIGN:OWN','CALENDAR_EVENT:ASSIGN:TEAM','CALENDAR_EVENT:ASSIGN:DEPARTMENT','CALENDAR_EVENT:ASSIGN:ORGANIZATION')")
    public ApiResponse<CalendarEventDto> assignOwner(
            @PathVariable UUID eventId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CalendarEventDto.from(calendarEventService.assignOwner(principal, eventId, request.ownerId())), "Owner updated");
    }

    @GetMapping("/{eventId}/attendees")
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:READ:OWN','CALENDAR_EVENT:READ:TEAM','CALENDAR_EVENT:READ:DEPARTMENT','CALENDAR_EVENT:READ:ORGANIZATION')")
    public ApiResponse<List<CalendarEventAttendeeDto>> listAttendees(@PathVariable UUID eventId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(calendarEventService.getAttendees(principal, eventId).stream().map(CalendarEventAttendeeDto::from).toList());
    }

    @PostMapping("/{eventId}/attendees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:UPDATE:OWN','CALENDAR_EVENT:UPDATE:TEAM','CALENDAR_EVENT:UPDATE:DEPARTMENT','CALENDAR_EVENT:UPDATE:ORGANIZATION')")
    public ApiResponse<CalendarEventAttendeeDto> addAttendee(
            @PathVariable UUID eventId, @Valid @RequestBody AddAttendeeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CalendarEventAttendeeDto.from(calendarEventService.addAttendee(principal, eventId, request)), "Attendee added");
    }

    @PatchMapping("/{eventId}/attendees/{attendeeId}/response")
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:UPDATE:OWN','CALENDAR_EVENT:UPDATE:TEAM','CALENDAR_EVENT:UPDATE:DEPARTMENT','CALENDAR_EVENT:UPDATE:ORGANIZATION')")
    public ApiResponse<CalendarEventAttendeeDto> updateAttendeeResponse(
            @PathVariable UUID eventId, @PathVariable UUID attendeeId,
            @Valid @RequestBody UpdateAttendeeResponseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CalendarEventAttendeeDto.from(calendarEventService.updateAttendeeResponse(principal, eventId, attendeeId, request.responseStatus())),
                "Response updated");
    }

    @DeleteMapping("/{eventId}/attendees/{attendeeId}")
    @PreAuthorize("hasAnyAuthority('CALENDAR_EVENT:UPDATE:OWN','CALENDAR_EVENT:UPDATE:TEAM','CALENDAR_EVENT:UPDATE:DEPARTMENT','CALENDAR_EVENT:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeAttendee(
            @PathVariable UUID eventId, @PathVariable UUID attendeeId, @AuthenticationPrincipal UserPrincipal principal) {
        calendarEventService.removeAttendee(principal, eventId, attendeeId);
        return ApiResponse.ok(null, "Attendee removed");
    }
}
