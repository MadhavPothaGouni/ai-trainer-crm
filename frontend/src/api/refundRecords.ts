import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateRefundRecordRequest,
  PageResponse,
  RefundRecordDto,
  UpdateRefundRecordRequest,
  UpdateRefundRecordStatusRequest,
} from "../types/api";

export interface ListRefundRecordsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listRefundRecords(params: ListRefundRecordsParams = {}): Promise<PageResponse<RefundRecordDto>> {
  return unwrap(apiClient.get("/api/v1/refund-records", { params }));
}

export function getRefundRecord(refundRecordId: string): Promise<RefundRecordDto> {
  return unwrap(apiClient.get(`/api/v1/refund-records/${refundRecordId}`));
}

export function createRefundRecord(request: CreateRefundRecordRequest): Promise<RefundRecordDto> {
  return unwrap(apiClient.post("/api/v1/refund-records", request));
}

export function updateRefundRecord(refundRecordId: string, request: UpdateRefundRecordRequest): Promise<RefundRecordDto> {
  return unwrap(apiClient.put(`/api/v1/refund-records/${refundRecordId}`, request));
}

export function updateRefundRecordStatus(refundRecordId: string, request: UpdateRefundRecordStatusRequest): Promise<RefundRecordDto> {
  return unwrap(apiClient.patch(`/api/v1/refund-records/${refundRecordId}/status`, request));
}

export function deleteRefundRecord(refundRecordId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/refund-records/${refundRecordId}`));
}
