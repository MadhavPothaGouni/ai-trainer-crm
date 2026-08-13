import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateSequenceEnrollmentRequest,
  CreateSequenceRequest,
  CreateSequenceStepRequest,
  PageResponse,
  SequenceDto,
  SequenceEnrollmentDto,
  SequenceStepDto,
  UpdateSequenceEnrollmentStatusRequest,
  UpdateSequenceRequest,
  UpdateSequenceStepRequest,
} from "../types/api";

export interface ListSequencesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listSequences(params: ListSequencesParams = {}): Promise<PageResponse<SequenceDto>> {
  return unwrap(apiClient.get("/api/v1/sequences", { params }));
}

/** The unpaginated active catalog - used by enrollment forms. See SequenceService#listActive's javadoc. */
export function listActiveSequences(): Promise<SequenceDto[]> {
  return unwrap(apiClient.get("/api/v1/sequences/active"));
}

export function getSequence(sequenceId: string): Promise<SequenceDto> {
  return unwrap(apiClient.get(`/api/v1/sequences/${sequenceId}`));
}

export function createSequence(request: CreateSequenceRequest): Promise<SequenceDto> {
  return unwrap(apiClient.post("/api/v1/sequences", request));
}

export function updateSequence(sequenceId: string, request: UpdateSequenceRequest): Promise<SequenceDto> {
  return unwrap(apiClient.put(`/api/v1/sequences/${sequenceId}`, request));
}

export function deleteSequence(sequenceId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/sequences/${sequenceId}`));
}

export function addSequenceStep(sequenceId: string, request: CreateSequenceStepRequest): Promise<SequenceStepDto> {
  return unwrap(apiClient.post(`/api/v1/sequences/${sequenceId}/steps`, request));
}

export function updateSequenceStep(sequenceId: string, stepId: string, request: UpdateSequenceStepRequest): Promise<SequenceStepDto> {
  return unwrap(apiClient.put(`/api/v1/sequences/${sequenceId}/steps/${stepId}`, request));
}

export function removeSequenceStep(sequenceId: string, stepId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/sequences/${sequenceId}/steps/${stepId}`));
}

export interface ListSequenceEnrollmentsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listSequenceEnrollments(params: ListSequenceEnrollmentsParams = {}): Promise<PageResponse<SequenceEnrollmentDto>> {
  return unwrap(apiClient.get("/api/v1/sequence-enrollments", { params }));
}

export function getSequenceEnrollment(enrollmentId: string): Promise<SequenceEnrollmentDto> {
  return unwrap(apiClient.get(`/api/v1/sequence-enrollments/${enrollmentId}`));
}

export function createSequenceEnrollment(request: CreateSequenceEnrollmentRequest): Promise<SequenceEnrollmentDto> {
  return unwrap(apiClient.post("/api/v1/sequence-enrollments", request));
}

/** See SequenceEnrollmentService#advance's javadoc - moves to the next step, auto-completing once the step list is exhausted. */
export function advanceSequenceEnrollment(enrollmentId: string): Promise<SequenceEnrollmentDto> {
  return unwrap(apiClient.patch(`/api/v1/sequence-enrollments/${enrollmentId}/advance`, {}));
}

export function updateSequenceEnrollmentStatus(
  enrollmentId: string,
  request: UpdateSequenceEnrollmentStatusRequest,
): Promise<SequenceEnrollmentDto> {
  return unwrap(apiClient.patch(`/api/v1/sequence-enrollments/${enrollmentId}/status`, request));
}

export function deleteSequenceEnrollment(enrollmentId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/sequence-enrollments/${enrollmentId}`));
}
