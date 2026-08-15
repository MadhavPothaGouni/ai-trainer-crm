import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateGroupClassRequest, GroupClassDto, PageResponse, UpdateGroupClassRequest } from "../types/api";

export interface ListGroupClassesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listGroupClasses(params: ListGroupClassesParams = {}): Promise<PageResponse<GroupClassDto>> {
  return unwrap(apiClient.get("/api/v1/group-classes", { params }));
}

export function getGroupClass(groupClassId: string): Promise<GroupClassDto> {
  return unwrap(apiClient.get(`/api/v1/group-classes/${groupClassId}`));
}

export function createGroupClass(request: CreateGroupClassRequest): Promise<GroupClassDto> {
  return unwrap(apiClient.post("/api/v1/group-classes", request));
}

export function updateGroupClass(groupClassId: string, request: UpdateGroupClassRequest): Promise<GroupClassDto> {
  return unwrap(apiClient.put(`/api/v1/group-classes/${groupClassId}`, request));
}

export function deleteGroupClass(groupClassId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/group-classes/${groupClassId}`));
}
