import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateLockerAssignmentRequest,
  LockerAssignmentDto,
  PageResponse,
  UpdateLockerAssignmentRequest,
  UpdateLockerAssignmentStatusRequest,
} from "../types/api";

export interface ListLockerAssignmentsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listLockerAssignments(params: ListLockerAssignmentsParams = {}): Promise<PageResponse<LockerAssignmentDto>> {
  return unwrap(apiClient.get("/api/v1/locker-assignments", { params }));
}

export function getLockerAssignment(lockerAssignmentId: string): Promise<LockerAssignmentDto> {
  return unwrap(apiClient.get(`/api/v1/locker-assignments/${lockerAssignmentId}`));
}

export function createLockerAssignment(request: CreateLockerAssignmentRequest): Promise<LockerAssignmentDto> {
  return unwrap(apiClient.post("/api/v1/locker-assignments", request));
}

export function updateLockerAssignment(
  lockerAssignmentId: string,
  request: UpdateLockerAssignmentRequest,
): Promise<LockerAssignmentDto> {
  return unwrap(apiClient.put(`/api/v1/locker-assignments/${lockerAssignmentId}`, request));
}

export function updateLockerAssignmentStatus(
  lockerAssignmentId: string,
  request: UpdateLockerAssignmentStatusRequest,
): Promise<LockerAssignmentDto> {
  return unwrap(apiClient.patch(`/api/v1/locker-assignments/${lockerAssignmentId}/status`, request));
}

export function deleteLockerAssignment(lockerAssignmentId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/locker-assignments/${lockerAssignmentId}`));
}
