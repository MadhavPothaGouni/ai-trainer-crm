import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateCustomObjectRecordRequest,
  CreateCustomObjectRequest,
  CustomObjectDto,
  CustomObjectRecordDto,
  PageResponse,
  UpdateCustomObjectRecordRequest,
  UpdateCustomObjectRequest,
} from "../types/api";

export interface ListParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listCustomObjects(params: ListParams = {}): Promise<PageResponse<CustomObjectDto>> {
  return unwrap(apiClient.get("/api/v1/custom-objects", { params }));
}

export function getCustomObject(customObjectId: string): Promise<CustomObjectDto> {
  return unwrap(apiClient.get(`/api/v1/custom-objects/${customObjectId}`));
}

export function createCustomObject(request: CreateCustomObjectRequest): Promise<CustomObjectDto> {
  return unwrap(apiClient.post("/api/v1/custom-objects", request));
}

export function updateCustomObject(customObjectId: string, request: UpdateCustomObjectRequest): Promise<CustomObjectDto> {
  return unwrap(apiClient.put(`/api/v1/custom-objects/${customObjectId}`, request));
}

export function deleteCustomObject(customObjectId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/custom-objects/${customObjectId}`));
}

export function listCustomObjectRecords(
  customObjectId: string,
  params: ListParams = {},
): Promise<PageResponse<CustomObjectRecordDto>> {
  return unwrap(apiClient.get(`/api/v1/custom-objects/${customObjectId}/records`, { params }));
}

export function getCustomObjectRecord(customObjectId: string, recordId: string): Promise<CustomObjectRecordDto> {
  return unwrap(apiClient.get(`/api/v1/custom-objects/${customObjectId}/records/${recordId}`));
}

export function createCustomObjectRecord(
  customObjectId: string,
  request: CreateCustomObjectRecordRequest,
): Promise<CustomObjectRecordDto> {
  return unwrap(apiClient.post(`/api/v1/custom-objects/${customObjectId}/records`, request));
}

export function updateCustomObjectRecord(
  customObjectId: string,
  recordId: string,
  request: UpdateCustomObjectRecordRequest,
): Promise<CustomObjectRecordDto> {
  return unwrap(apiClient.put(`/api/v1/custom-objects/${customObjectId}/records/${recordId}`, request));
}

export function deleteCustomObjectRecord(customObjectId: string, recordId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/custom-objects/${customObjectId}/records/${recordId}`));
}
