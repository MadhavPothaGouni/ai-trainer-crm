import { apiClient, unwrap } from "../lib/apiClient";
import type {
  ClassAttendanceDto,
  CreateClassAttendanceRequest,
  PageResponse,
  UpdateClassAttendanceRequest,
  UpdateClassAttendanceStatusRequest,
} from "../types/api";

export interface ListClassAttendancesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listClassAttendances(params: ListClassAttendancesParams = {}): Promise<PageResponse<ClassAttendanceDto>> {
  return unwrap(apiClient.get("/api/v1/class-attendances", { params }));
}

export function getClassAttendance(classAttendanceId: string): Promise<ClassAttendanceDto> {
  return unwrap(apiClient.get(`/api/v1/class-attendances/${classAttendanceId}`));
}

export function createClassAttendance(request: CreateClassAttendanceRequest): Promise<ClassAttendanceDto> {
  return unwrap(apiClient.post("/api/v1/class-attendances", request));
}

export function updateClassAttendance(classAttendanceId: string, request: UpdateClassAttendanceRequest): Promise<ClassAttendanceDto> {
  return unwrap(apiClient.put(`/api/v1/class-attendances/${classAttendanceId}`, request));
}

export function updateClassAttendanceStatus(classAttendanceId: string, request: UpdateClassAttendanceStatusRequest): Promise<ClassAttendanceDto> {
  return unwrap(apiClient.patch(`/api/v1/class-attendances/${classAttendanceId}/status`, request));
}

export function deleteClassAttendance(classAttendanceId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/class-attendances/${classAttendanceId}`));
}
