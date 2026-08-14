import { apiClient, unwrap } from "../lib/apiClient";
import type { BodyMeasurementDto, CreateBodyMeasurementRequest, PageResponse, UpdateBodyMeasurementRequest } from "../types/api";

export interface ListBodyMeasurementsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listBodyMeasurements(params: ListBodyMeasurementsParams = {}): Promise<PageResponse<BodyMeasurementDto>> {
  return unwrap(apiClient.get("/api/v1/body-measurements", { params }));
}

export function getBodyMeasurement(bodyMeasurementId: string): Promise<BodyMeasurementDto> {
  return unwrap(apiClient.get(`/api/v1/body-measurements/${bodyMeasurementId}`));
}

export function createBodyMeasurement(request: CreateBodyMeasurementRequest): Promise<BodyMeasurementDto> {
  return unwrap(apiClient.post("/api/v1/body-measurements", request));
}

export function updateBodyMeasurement(bodyMeasurementId: string, request: UpdateBodyMeasurementRequest): Promise<BodyMeasurementDto> {
  return unwrap(apiClient.put(`/api/v1/body-measurements/${bodyMeasurementId}`, request));
}

export function deleteBodyMeasurement(bodyMeasurementId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/body-measurements/${bodyMeasurementId}`));
}
