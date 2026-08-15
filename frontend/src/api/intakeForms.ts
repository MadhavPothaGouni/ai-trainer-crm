import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateIntakeFormRequest, IntakeFormDto, PageResponse, UpdateIntakeFormRequest } from "../types/api";

export interface ListIntakeFormsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listIntakeForms(params: ListIntakeFormsParams = {}): Promise<PageResponse<IntakeFormDto>> {
  return unwrap(apiClient.get("/api/v1/intake-forms", { params }));
}

export function getIntakeForm(intakeFormId: string): Promise<IntakeFormDto> {
  return unwrap(apiClient.get(`/api/v1/intake-forms/${intakeFormId}`));
}

export function createIntakeForm(request: CreateIntakeFormRequest): Promise<IntakeFormDto> {
  return unwrap(apiClient.post("/api/v1/intake-forms", request));
}

export function updateIntakeForm(intakeFormId: string, request: UpdateIntakeFormRequest): Promise<IntakeFormDto> {
  return unwrap(apiClient.put(`/api/v1/intake-forms/${intakeFormId}`, request));
}

export function deleteIntakeForm(intakeFormId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/intake-forms/${intakeFormId}`));
}
