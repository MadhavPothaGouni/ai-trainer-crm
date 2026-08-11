import { apiClient, unwrap } from "../lib/apiClient";
import type {
  AddAttendeeRequest,
  AssignOwnerRequest,
  CalendarEventAttendeeDto,
  CalendarEventDto,
  CreateCalendarEventRequest,
  CrmRecordType,
  PageResponse,
  UpdateAttendeeResponseRequest,
  UpdateCalendarEventRequest,
} from "../types/api";

export interface ListCalendarEventsParams {
  page?: number;
  size?: number;
  sort?: string;
  relatedToType?: CrmRecordType;
  relatedToId?: string;
}

export function listCalendarEvents(params: ListCalendarEventsParams = {}): Promise<PageResponse<CalendarEventDto>> {
  return unwrap(apiClient.get("/api/v1/calendar-events", { params }));
}

export function getCalendarEvent(eventId: string): Promise<CalendarEventDto> {
  return unwrap(apiClient.get(`/api/v1/calendar-events/${eventId}`));
}

export function createCalendarEvent(request: CreateCalendarEventRequest): Promise<CalendarEventDto> {
  return unwrap(apiClient.post("/api/v1/calendar-events", request));
}

export function updateCalendarEvent(eventId: string, request: UpdateCalendarEventRequest): Promise<CalendarEventDto> {
  return unwrap(apiClient.put(`/api/v1/calendar-events/${eventId}`, request));
}

export function deleteCalendarEvent(eventId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/calendar-events/${eventId}`));
}

export function assignCalendarEventOwner(eventId: string, request: AssignOwnerRequest): Promise<CalendarEventDto> {
  return unwrap(apiClient.patch(`/api/v1/calendar-events/${eventId}/owner`, request));
}

export function listAttendees(eventId: string): Promise<CalendarEventAttendeeDto[]> {
  return unwrap(apiClient.get(`/api/v1/calendar-events/${eventId}/attendees`));
}

export function addAttendee(eventId: string, request: AddAttendeeRequest): Promise<CalendarEventAttendeeDto> {
  return unwrap(apiClient.post(`/api/v1/calendar-events/${eventId}/attendees`, request));
}

export function updateAttendeeResponse(
  eventId: string, attendeeId: string, request: UpdateAttendeeResponseRequest): Promise<CalendarEventAttendeeDto> {
  return unwrap(apiClient.patch(`/api/v1/calendar-events/${eventId}/attendees/${attendeeId}/response`, request));
}

export function removeAttendee(eventId: string, attendeeId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/calendar-events/${eventId}/attendees/${attendeeId}`));
}
