import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateIntakeFormSubmissionRequest,
  IntakeFormSubmissionDto,
  PageResponse,
  UpdateIntakeFormSubmissionRequest,
} from "../types/api";

export interface ListIntakeFormSubmissionsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listIntakeFormSubmissions(params: ListIntakeFormSubmissionsParams = {}): Promise<PageResponse<IntakeFormSubmissionDto>> {
  return unwrap(apiClient.get("/api/v1/intake-form-submissions", { params }));
}

export function getIntakeFormSubmission(intakeFormSubmissionId: string): Promise<IntakeFormSubmissionDto> {
  return unwrap(apiClient.get(`/api/v1/intake-form-submissions/${intakeFormSubmissionId}`));
}

export function createIntakeFormSubmission(request: CreateIntakeFormSubmissionRequest): Promise<IntakeFormSubmissionDto> {
  return unwrap(apiClient.post("/api/v1/intake-form-submissions", request));
}

export function updateIntakeFormSubmission(
  intakeFormSubmissionId: string,
  request: UpdateIntakeFormSubmissionRequest,
): Promise<IntakeFormSubmissionDto> {
  return unwrap(apiClient.put(`/api/v1/intake-form-submissions/${intakeFormSubmissionId}`, request));
}

export function deleteIntakeFormSubmission(intakeFormSubmissionId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/intake-form-submissions/${intakeFormSubmissionId}`));
}
