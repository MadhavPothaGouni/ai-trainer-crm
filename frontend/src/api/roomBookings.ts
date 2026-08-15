import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateRoomBookingRequest,
  PageResponse,
  RoomBookingDto,
  UpdateRoomBookingRequest,
  UpdateRoomBookingStatusRequest,
} from "../types/api";

export interface ListRoomBookingsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listRoomBookings(params: ListRoomBookingsParams = {}): Promise<PageResponse<RoomBookingDto>> {
  return unwrap(apiClient.get("/api/v1/room-bookings", { params }));
}

export function getRoomBooking(roomBookingId: string): Promise<RoomBookingDto> {
  return unwrap(apiClient.get(`/api/v1/room-bookings/${roomBookingId}`));
}

export function createRoomBooking(request: CreateRoomBookingRequest): Promise<RoomBookingDto> {
  return unwrap(apiClient.post("/api/v1/room-bookings", request));
}

export function updateRoomBooking(roomBookingId: string, request: UpdateRoomBookingRequest): Promise<RoomBookingDto> {
  return unwrap(apiClient.put(`/api/v1/room-bookings/${roomBookingId}`, request));
}

export function updateRoomBookingStatus(
  roomBookingId: string,
  request: UpdateRoomBookingStatusRequest,
): Promise<RoomBookingDto> {
  return unwrap(apiClient.patch(`/api/v1/room-bookings/${roomBookingId}/status`, request));
}

export function deleteRoomBooking(roomBookingId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/room-bookings/${roomBookingId}`));
}
