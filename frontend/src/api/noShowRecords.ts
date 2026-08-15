import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateNoShowRecordRequest, NoShowRecordDto, PageResponse, UpdateNoShowRecordRequest } from "../types/api";

export interface ListNoShowRecordsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listNoShowRecords(params: ListNoShowRecordsParams = {}): Promise<PageResponse<NoShowRecordDto>> {
  return unwrap(apiClient.get("/api/v1/no-show-records", { params }));
}

export function getNoShowRecord(noShowRecordId: string): Promise<NoShowRecordDto> {
  return unwrap(apiClient.get(`/api/v1/no-show-records/${noShowRecordId}`));
}

export function createNoShowRecord(request: CreateNoShowRecordRequest): Promise<NoShowRecordDto> {
  return unwrap(apiClient.post("/api/v1/no-show-records", request));
}

export function updateNoShowRecord(noShowRecordId: string, request: UpdateNoShowRecordRequest): Promise<NoShowRecordDto> {
  return unwrap(apiClient.put(`/api/v1/no-show-records/${noShowRecordId}`, request));
}

export function waiveNoShowRecord(noShowRecordId: string): Promise<NoShowRecordDto> {
  return unwrap(apiClient.post(`/api/v1/no-show-records/${noShowRecordId}/waive`));
}

export function deleteNoShowRecord(noShowRecordId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/no-show-records/${noShowRecordId}`));
}
