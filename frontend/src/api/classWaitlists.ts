import { apiClient, unwrap } from "../lib/apiClient";
import type {
  ClassWaitlistDto,
  CreateClassWaitlistRequest,
  PageResponse,
  UpdateClassWaitlistRequest,
  UpdateClassWaitlistStatusRequest,
} from "../types/api";

export interface ListClassWaitlistsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listClassWaitlists(params: ListClassWaitlistsParams = {}): Promise<PageResponse<ClassWaitlistDto>> {
  return unwrap(apiClient.get("/api/v1/class-waitlists", { params }));
}

export function getClassWaitlist(classWaitlistId: string): Promise<ClassWaitlistDto> {
  return unwrap(apiClient.get(`/api/v1/class-waitlists/${classWaitlistId}`));
}

export function createClassWaitlist(request: CreateClassWaitlistRequest): Promise<ClassWaitlistDto> {
  return unwrap(apiClient.post("/api/v1/class-waitlists", request));
}

export function updateClassWaitlist(classWaitlistId: string, request: UpdateClassWaitlistRequest): Promise<ClassWaitlistDto> {
  return unwrap(apiClient.put(`/api/v1/class-waitlists/${classWaitlistId}`, request));
}

export function updateClassWaitlistStatus(
  classWaitlistId: string,
  request: UpdateClassWaitlistStatusRequest,
): Promise<ClassWaitlistDto> {
  return unwrap(apiClient.patch(`/api/v1/class-waitlists/${classWaitlistId}/status`, request));
}

export function deleteClassWaitlist(classWaitlistId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/class-waitlists/${classWaitlistId}`));
}
