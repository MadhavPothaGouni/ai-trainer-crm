import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateSavedViewRequest, SavedViewDto, SavedViewEntityType, UpdateSavedViewRequest } from "../types/api";

/** No permission required - always scoped to the caller's own views for this entity type. */
export function listSavedViews(entityType: SavedViewEntityType): Promise<SavedViewDto[]> {
  return unwrap(apiClient.get("/api/v1/saved-views", { params: { entityType } }));
}

export function createSavedView(request: CreateSavedViewRequest): Promise<SavedViewDto> {
  return unwrap(apiClient.post("/api/v1/saved-views", request));
}

export function updateSavedView(viewId: string, request: UpdateSavedViewRequest): Promise<SavedViewDto> {
  return unwrap(apiClient.put(`/api/v1/saved-views/${viewId}`, request));
}

export function setDefaultSavedView(viewId: string): Promise<SavedViewDto> {
  return unwrap(apiClient.patch(`/api/v1/saved-views/${viewId}/default`));
}

export function deleteSavedView(viewId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/saved-views/${viewId}`));
}
