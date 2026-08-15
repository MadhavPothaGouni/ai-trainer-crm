import { apiClient, unwrap } from "../lib/apiClient";
import type {
  ClassSessionDto,
  CreateClassSessionRequest,
  PageResponse,
  UpdateClassSessionRequest,
  UpdateClassSessionStatusRequest,
} from "../types/api";

export interface ListClassSessionsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listClassSessions(params: ListClassSessionsParams = {}): Promise<PageResponse<ClassSessionDto>> {
  return unwrap(apiClient.get("/api/v1/class-sessions", { params }));
}

export function getClassSession(classSessionId: string): Promise<ClassSessionDto> {
  return unwrap(apiClient.get(`/api/v1/class-sessions/${classSessionId}`));
}

export function createClassSession(request: CreateClassSessionRequest): Promise<ClassSessionDto> {
  return unwrap(apiClient.post("/api/v1/class-sessions", request));
}

export function updateClassSession(classSessionId: string, request: UpdateClassSessionRequest): Promise<ClassSessionDto> {
  return unwrap(apiClient.put(`/api/v1/class-sessions/${classSessionId}`, request));
}

export function updateClassSessionStatus(classSessionId: string, request: UpdateClassSessionStatusRequest): Promise<ClassSessionDto> {
  return unwrap(apiClient.patch(`/api/v1/class-sessions/${classSessionId}/status`, request));
}

export function deleteClassSession(classSessionId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/class-sessions/${classSessionId}`));
}
