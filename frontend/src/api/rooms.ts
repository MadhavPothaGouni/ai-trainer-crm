import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateRoomRequest, PageResponse, RoomDto, UpdateRoomRequest } from "../types/api";

export interface ListRoomsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listRooms(params: ListRoomsParams = {}): Promise<PageResponse<RoomDto>> {
  return unwrap(apiClient.get("/api/v1/rooms", { params }));
}

export function getRoom(roomId: string): Promise<RoomDto> {
  return unwrap(apiClient.get(`/api/v1/rooms/${roomId}`));
}

export function createRoom(request: CreateRoomRequest): Promise<RoomDto> {
  return unwrap(apiClient.post("/api/v1/rooms", request));
}

export function updateRoom(roomId: string, request: UpdateRoomRequest): Promise<RoomDto> {
  return unwrap(apiClient.put(`/api/v1/rooms/${roomId}`, request));
}

export function deleteRoom(roomId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/rooms/${roomId}`));
}
