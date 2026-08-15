import { apiClient, unwrap } from "../lib/apiClient";
import type { CreatePersonalRecordRequest, PageResponse, PersonalRecordDto, UpdatePersonalRecordRequest } from "../types/api";

export interface ListPersonalRecordsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listPersonalRecords(params: ListPersonalRecordsParams = {}): Promise<PageResponse<PersonalRecordDto>> {
  return unwrap(apiClient.get("/api/v1/personal-records", { params }));
}

export function getPersonalRecord(personalRecordId: string): Promise<PersonalRecordDto> {
  return unwrap(apiClient.get(`/api/v1/personal-records/${personalRecordId}`));
}

export function createPersonalRecord(request: CreatePersonalRecordRequest): Promise<PersonalRecordDto> {
  return unwrap(apiClient.post("/api/v1/personal-records", request));
}

export function updatePersonalRecord(personalRecordId: string, request: UpdatePersonalRecordRequest): Promise<PersonalRecordDto> {
  return unwrap(apiClient.put(`/api/v1/personal-records/${personalRecordId}`, request));
}

export function deletePersonalRecord(personalRecordId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/personal-records/${personalRecordId}`));
}
