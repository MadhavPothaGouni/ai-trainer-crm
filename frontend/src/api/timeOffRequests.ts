import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateTimeOffRequestRequest,
  PageResponse,
  TimeOffRequestDto,
  UpdateTimeOffRequestRequest,
  UpdateTimeOffRequestStatusRequest,
} from "../types/api";

export interface ListTimeOffRequestsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listTimeOffRequests(params: ListTimeOffRequestsParams = {}): Promise<PageResponse<TimeOffRequestDto>> {
  return unwrap(apiClient.get("/api/v1/time-off-requests", { params }));
}

export function getTimeOffRequest(timeOffRequestId: string): Promise<TimeOffRequestDto> {
  return unwrap(apiClient.get(`/api/v1/time-off-requests/${timeOffRequestId}`));
}

export function createTimeOffRequest(request: CreateTimeOffRequestRequest): Promise<TimeOffRequestDto> {
  return unwrap(apiClient.post("/api/v1/time-off-requests", request));
}

export function updateTimeOffRequest(timeOffRequestId: string, request: UpdateTimeOffRequestRequest): Promise<TimeOffRequestDto> {
  return unwrap(apiClient.put(`/api/v1/time-off-requests/${timeOffRequestId}`, request));
}

export function updateTimeOffRequestStatus(
  timeOffRequestId: string,
  request: UpdateTimeOffRequestStatusRequest,
): Promise<TimeOffRequestDto> {
  return unwrap(apiClient.patch(`/api/v1/time-off-requests/${timeOffRequestId}/status`, request));
}

export function deleteTimeOffRequest(timeOffRequestId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/time-off-requests/${timeOffRequestId}`));
}
