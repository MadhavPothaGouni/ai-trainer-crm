import { apiClient, unwrap } from "../lib/apiClient";
import type { ApprovalRequestDto, ApprovalTaskDto, CreateApprovalRequestRequest, DecideStepRequest, PageResponse } from "../types/api";

export interface ListApprovalRequestsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listApprovalRequests(params: ListApprovalRequestsParams = {}): Promise<PageResponse<ApprovalRequestDto>> {
  return unwrap(apiClient.get("/api/v1/approval-requests", { params }));
}

/** The caller's pending-approval inbox - see ApprovalTaskDto's javadoc for why this is a separately-shaped read rather than a filtered view of listApprovalRequests. */
export function listMyApprovalTasks(params: ListApprovalRequestsParams = {}): Promise<PageResponse<ApprovalTaskDto>> {
  return unwrap(apiClient.get("/api/v1/approval-requests/my-approvals", { params }));
}

export function getApprovalRequest(requestId: string): Promise<ApprovalRequestDto> {
  return unwrap(apiClient.get(`/api/v1/approval-requests/${requestId}`));
}

export function createApprovalRequest(request: CreateApprovalRequestRequest): Promise<ApprovalRequestDto> {
  return unwrap(apiClient.post("/api/v1/approval-requests", request));
}

export function approveApprovalStep(requestId: string, stepNumber: number, request: DecideStepRequest = {}): Promise<ApprovalRequestDto> {
  return unwrap(apiClient.post(`/api/v1/approval-requests/${requestId}/steps/${stepNumber}/approve`, request));
}

export function rejectApprovalStep(requestId: string, stepNumber: number, request: DecideStepRequest = {}): Promise<ApprovalRequestDto> {
  return unwrap(apiClient.post(`/api/v1/approval-requests/${requestId}/steps/${stepNumber}/reject`, request));
}

export function cancelApprovalRequest(requestId: string): Promise<ApprovalRequestDto> {
  return unwrap(apiClient.patch(`/api/v1/approval-requests/${requestId}/cancel`));
}
