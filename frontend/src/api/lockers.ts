import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateLockerRequest, LockerDto, PageResponse, UpdateLockerRequest } from "../types/api";

export interface ListLockersParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listLockers(params: ListLockersParams = {}): Promise<PageResponse<LockerDto>> {
  return unwrap(apiClient.get("/api/v1/lockers", { params }));
}

export function getLocker(lockerId: string): Promise<LockerDto> {
  return unwrap(apiClient.get(`/api/v1/lockers/${lockerId}`));
}

export function createLocker(request: CreateLockerRequest): Promise<LockerDto> {
  return unwrap(apiClient.post("/api/v1/lockers", request));
}

export function updateLocker(lockerId: string, request: UpdateLockerRequest): Promise<LockerDto> {
  return unwrap(apiClient.put(`/api/v1/lockers/${lockerId}`, request));
}

export function deleteLocker(lockerId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/lockers/${lockerId}`));
}
