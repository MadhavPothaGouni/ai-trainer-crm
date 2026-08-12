import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateTerritoryRuleRequest, PageResponse, TerritoryRuleDto, UpdateTerritoryRuleRequest } from "../types/api";

export interface ListTerritoryRulesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listTerritoryRules(params: ListTerritoryRulesParams = {}): Promise<PageResponse<TerritoryRuleDto>> {
  return unwrap(apiClient.get("/api/v1/territory-rules", { params }));
}

export function getTerritoryRule(ruleId: string): Promise<TerritoryRuleDto> {
  return unwrap(apiClient.get(`/api/v1/territory-rules/${ruleId}`));
}

export function createTerritoryRule(request: CreateTerritoryRuleRequest): Promise<TerritoryRuleDto> {
  return unwrap(apiClient.post("/api/v1/territory-rules", request));
}

export function updateTerritoryRule(ruleId: string, request: UpdateTerritoryRuleRequest): Promise<TerritoryRuleDto> {
  return unwrap(apiClient.put(`/api/v1/territory-rules/${ruleId}`, request));
}

export function deleteTerritoryRule(ruleId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/territory-rules/${ruleId}`));
}
