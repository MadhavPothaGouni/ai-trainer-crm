import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateCustomFieldRequest,
  CustomFieldDto,
  CustomFieldValueDto,
  SetCustomFieldValuesRequest,
  StandardEntityType,
  UpdateCustomFieldRequest,
} from "../types/api";

/** Exactly one of `standardEntityType`/`customObjectId` should be set - mirrors the backend's own exactly-one-of-target validation. */
export interface FieldTarget {
  standardEntityType?: StandardEntityType;
  customObjectId?: string;
}

export function listCustomFields(target: FieldTarget): Promise<CustomFieldDto[]> {
  return unwrap(apiClient.get("/api/v1/custom-fields", { params: target }));
}

export function getCustomField(fieldId: string): Promise<CustomFieldDto> {
  return unwrap(apiClient.get(`/api/v1/custom-fields/${fieldId}`));
}

export function createCustomField(request: CreateCustomFieldRequest): Promise<CustomFieldDto> {
  return unwrap(apiClient.post("/api/v1/custom-fields", request));
}

export function updateCustomField(fieldId: string, request: UpdateCustomFieldRequest): Promise<CustomFieldDto> {
  return unwrap(apiClient.put(`/api/v1/custom-fields/${fieldId}`, request));
}

export function deleteCustomField(fieldId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/custom-fields/${fieldId}`));
}

export function getCustomFieldValues(target: FieldTarget, recordId: string): Promise<CustomFieldValueDto[]> {
  return unwrap(apiClient.get("/api/v1/custom-fields/values", { params: { ...target, recordId } }));
}

export function setCustomFieldValues(
  target: FieldTarget,
  recordId: string,
  request: SetCustomFieldValuesRequest,
): Promise<CustomFieldValueDto[]> {
  return unwrap(apiClient.put("/api/v1/custom-fields/values", request, { params: { ...target, recordId } }));
}
