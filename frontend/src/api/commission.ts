import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CommissionPlanDto,
  CommissionRecordDto,
  CreateCommissionPlanRequest,
  PageResponse,
  UpdateCommissionPlanRequest,
  UpdateCommissionRecordStatusRequest,
} from "../types/api";

export interface ListParams {
  page?: number;
  size?: number;
  sort?: string;
}

// ---- Commission Plans (admin config, COMMISSION_PLAN:*:ORGANIZATION) ----

export function listCommissionPlans(params: ListParams = {}): Promise<PageResponse<CommissionPlanDto>> {
  return unwrap(apiClient.get("/api/v1/commission-plans", { params }));
}

export function getCommissionPlan(planId: string): Promise<CommissionPlanDto> {
  return unwrap(apiClient.get(`/api/v1/commission-plans/${planId}`));
}

export function createCommissionPlan(request: CreateCommissionPlanRequest): Promise<CommissionPlanDto> {
  return unwrap(apiClient.post("/api/v1/commission-plans", request));
}

export function updateCommissionPlan(planId: string, request: UpdateCommissionPlanRequest): Promise<CommissionPlanDto> {
  return unwrap(apiClient.put(`/api/v1/commission-plans/${planId}`, request));
}

export function deleteCommissionPlan(planId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/commission-plans/${planId}`));
}

// ---- Commission Records (never created/edited directly - CommissionEngine is the only writer
// of everything but status/paidAt) ----

export function listCommissionRecords(params: ListParams = {}): Promise<PageResponse<CommissionRecordDto>> {
  return unwrap(apiClient.get("/api/v1/commission-records", { params }));
}

/** No COMMISSION_RECORD permission required - always just the caller's own earned commissions. */
export function myCommissionRecords(): Promise<CommissionRecordDto[]> {
  return unwrap(apiClient.get("/api/v1/commission-records/mine"));
}

export function getCommissionRecord(recordId: string): Promise<CommissionRecordDto> {
  return unwrap(apiClient.get(`/api/v1/commission-records/${recordId}`));
}

export function updateCommissionRecordStatus(
  recordId: string,
  request: UpdateCommissionRecordStatusRequest,
): Promise<CommissionRecordDto> {
  return unwrap(apiClient.patch(`/api/v1/commission-records/${recordId}/status`, request));
}
