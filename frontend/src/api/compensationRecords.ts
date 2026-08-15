import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CompensationRecordDto,
  CreateCompensationRecordRequest,
  PageResponse,
  UpdateCompensationRecordRequest,
  UpdateCompensationRecordStatusRequest,
} from "../types/api";

export interface ListCompensationRecordsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listCompensationRecords(params: ListCompensationRecordsParams = {}): Promise<PageResponse<CompensationRecordDto>> {
  return unwrap(apiClient.get("/api/v1/compensation-records", { params }));
}

export function getCompensationRecord(compensationRecordId: string): Promise<CompensationRecordDto> {
  return unwrap(apiClient.get(`/api/v1/compensation-records/${compensationRecordId}`));
}

export function createCompensationRecord(request: CreateCompensationRecordRequest): Promise<CompensationRecordDto> {
  return unwrap(apiClient.post("/api/v1/compensation-records", request));
}

export function updateCompensationRecord(
  compensationRecordId: string,
  request: UpdateCompensationRecordRequest,
): Promise<CompensationRecordDto> {
  return unwrap(apiClient.put(`/api/v1/compensation-records/${compensationRecordId}`, request));
}

export function updateCompensationRecordStatus(
  compensationRecordId: string,
  request: UpdateCompensationRecordStatusRequest,
): Promise<CompensationRecordDto> {
  return unwrap(apiClient.patch(`/api/v1/compensation-records/${compensationRecordId}/status`, request));
}

export function deleteCompensationRecord(compensationRecordId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/compensation-records/${compensationRecordId}`));
}
