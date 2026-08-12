import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateSalesGoalRequest, PageResponse, SalesGoalDto, UpdateSalesGoalRequest } from "../types/api";

export interface ListSalesGoalsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listSalesGoals(params: ListSalesGoalsParams = {}): Promise<PageResponse<SalesGoalDto>> {
  return unwrap(apiClient.get("/api/v1/sales-goals", { params }));
}

/** No SALES_GOAL permission required - always returns just the caller's own individual + team goals. */
export function myGoals(): Promise<SalesGoalDto[]> {
  return unwrap(apiClient.get("/api/v1/sales-goals/mine"));
}

export function getSalesGoal(goalId: string): Promise<SalesGoalDto> {
  return unwrap(apiClient.get(`/api/v1/sales-goals/${goalId}`));
}

export function createSalesGoal(request: CreateSalesGoalRequest): Promise<SalesGoalDto> {
  return unwrap(apiClient.post("/api/v1/sales-goals", request));
}

export function updateSalesGoal(goalId: string, request: UpdateSalesGoalRequest): Promise<SalesGoalDto> {
  return unwrap(apiClient.put(`/api/v1/sales-goals/${goalId}`, request));
}

export function deleteSalesGoal(goalId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/sales-goals/${goalId}`));
}
