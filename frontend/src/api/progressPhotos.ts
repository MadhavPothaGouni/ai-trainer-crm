import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateProgressPhotoRequest, PageResponse, ProgressPhotoDto, UpdateProgressPhotoRequest } from "../types/api";

export interface ListProgressPhotosParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listProgressPhotos(params: ListProgressPhotosParams = {}): Promise<PageResponse<ProgressPhotoDto>> {
  return unwrap(apiClient.get("/api/v1/progress-photos", { params }));
}

export function getProgressPhoto(progressPhotoId: string): Promise<ProgressPhotoDto> {
  return unwrap(apiClient.get(`/api/v1/progress-photos/${progressPhotoId}`));
}

export function createProgressPhoto(request: CreateProgressPhotoRequest): Promise<ProgressPhotoDto> {
  return unwrap(apiClient.post("/api/v1/progress-photos", request));
}

export function updateProgressPhoto(progressPhotoId: string, request: UpdateProgressPhotoRequest): Promise<ProgressPhotoDto> {
  return unwrap(apiClient.put(`/api/v1/progress-photos/${progressPhotoId}`, request));
}

export function deleteProgressPhoto(progressPhotoId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/progress-photos/${progressPhotoId}`));
}
