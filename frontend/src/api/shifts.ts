import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateShiftRequest,
  PageResponse,
  ShiftDto,
  UpdateShiftRequest,
  UpdateShiftStatusRequest,
} from "../types/api";

export interface ListShiftsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listShifts(params: ListShiftsParams = {}): Promise<PageResponse<ShiftDto>> {
  return unwrap(apiClient.get("/api/v1/shifts", { params }));
}

export function getShift(shiftId: string): Promise<ShiftDto> {
  return unwrap(apiClient.get(`/api/v1/shifts/${shiftId}`));
}

export function createShift(request: CreateShiftRequest): Promise<ShiftDto> {
  return unwrap(apiClient.post("/api/v1/shifts", request));
}

export function updateShift(shiftId: string, request: UpdateShiftRequest): Promise<ShiftDto> {
  return unwrap(apiClient.put(`/api/v1/shifts/${shiftId}`, request));
}

export function updateShiftStatus(shiftId: string, request: UpdateShiftStatusRequest): Promise<ShiftDto> {
  return unwrap(apiClient.patch(`/api/v1/shifts/${shiftId}/status`, request));
}

export function deleteShift(shiftId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/shifts/${shiftId}`));
}
