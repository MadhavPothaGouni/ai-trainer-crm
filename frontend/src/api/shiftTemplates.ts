import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateShiftTemplateRequest, PageResponse, ShiftTemplateDto, UpdateShiftTemplateRequest } from "../types/api";

export interface ListShiftTemplatesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listShiftTemplates(params: ListShiftTemplatesParams = {}): Promise<PageResponse<ShiftTemplateDto>> {
  return unwrap(apiClient.get("/api/v1/shift-templates", { params }));
}

export function getShiftTemplate(shiftTemplateId: string): Promise<ShiftTemplateDto> {
  return unwrap(apiClient.get(`/api/v1/shift-templates/${shiftTemplateId}`));
}

export function createShiftTemplate(request: CreateShiftTemplateRequest): Promise<ShiftTemplateDto> {
  return unwrap(apiClient.post("/api/v1/shift-templates", request));
}

export function updateShiftTemplate(shiftTemplateId: string, request: UpdateShiftTemplateRequest): Promise<ShiftTemplateDto> {
  return unwrap(apiClient.put(`/api/v1/shift-templates/${shiftTemplateId}`, request));
}

export function deleteShiftTemplate(shiftTemplateId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/shift-templates/${shiftTemplateId}`));
}
