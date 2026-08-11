import { apiClient, unwrap } from "../lib/apiClient";
import type {
  ActivityDto,
  AssignOwnerRequest,
  CreateActivityRequest,
  PageResponse,
  RelatedToType,
  UpdateActivityRequest,
  UpdateActivityStatusRequest,
} from "../types/api";

export interface ListActivitiesParams {
  page?: number;
  size?: number;
  sort?: string;
  relatedToType?: RelatedToType;
  relatedToId?: string;
}

export function listActivities(params: ListActivitiesParams = {}): Promise<PageResponse<ActivityDto>> {
  return unwrap(apiClient.get("/api/v1/activities", { params }));
}

export function getActivity(activityId: string): Promise<ActivityDto> {
  return unwrap(apiClient.get(`/api/v1/activities/${activityId}`));
}

export function createActivity(request: CreateActivityRequest): Promise<ActivityDto> {
  return unwrap(apiClient.post("/api/v1/activities", request));
}

export function updateActivity(activityId: string, request: UpdateActivityRequest): Promise<ActivityDto> {
  return unwrap(apiClient.put(`/api/v1/activities/${activityId}`, request));
}

export function updateActivityStatus(activityId: string, request: UpdateActivityStatusRequest): Promise<ActivityDto> {
  return unwrap(apiClient.patch(`/api/v1/activities/${activityId}/status`, request));
}

export function deleteActivity(activityId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/activities/${activityId}`));
}

export function assignActivityOwner(activityId: string, request: AssignOwnerRequest): Promise<ActivityDto> {
  return unwrap(apiClient.patch(`/api/v1/activities/${activityId}/owner`, request));
}
