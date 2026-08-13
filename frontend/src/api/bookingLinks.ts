import { apiClient, unwrap } from "../lib/apiClient";
import type {
  BookSlotRequest,
  BookingLinkDto,
  BookingSlotDto,
  CreateBookingLinkRequest,
  CreateBookingSlotRequest,
  PageResponse,
  UpdateBookingLinkRequest,
} from "../types/api";

export interface ListBookingLinksParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listBookingLinks(params: ListBookingLinksParams = {}): Promise<PageResponse<BookingLinkDto>> {
  return unwrap(apiClient.get("/api/v1/booking-links", { params }));
}

export function getBookingLink(bookingLinkId: string): Promise<BookingLinkDto> {
  return unwrap(apiClient.get(`/api/v1/booking-links/${bookingLinkId}`));
}

export function createBookingLink(request: CreateBookingLinkRequest): Promise<BookingLinkDto> {
  return unwrap(apiClient.post("/api/v1/booking-links", request));
}

export function updateBookingLink(bookingLinkId: string, request: UpdateBookingLinkRequest): Promise<BookingLinkDto> {
  return unwrap(apiClient.put(`/api/v1/booking-links/${bookingLinkId}`, request));
}

export function deleteBookingLink(bookingLinkId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/booking-links/${bookingLinkId}`));
}

export function addBookingSlot(bookingLinkId: string, request: CreateBookingSlotRequest): Promise<BookingSlotDto> {
  return unwrap(apiClient.post(`/api/v1/booking-links/${bookingLinkId}/slots`, request));
}

export function removeBookingSlot(bookingLinkId: string, slotId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/booking-links/${bookingLinkId}/slots/${slotId}`));
}

export function bookSlot(bookingLinkId: string, slotId: string, request: BookSlotRequest): Promise<BookingSlotDto> {
  return unwrap(apiClient.patch(`/api/v1/booking-links/${bookingLinkId}/slots/${slotId}/book`, request));
}

export function cancelSlot(bookingLinkId: string, slotId: string): Promise<BookingSlotDto> {
  return unwrap(apiClient.patch(`/api/v1/booking-links/${bookingLinkId}/slots/${slotId}/cancel`, {}));
}
