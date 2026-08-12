import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateLeadScoringRuleRequest, LeadScoringRuleDto, PageResponse, UpdateLeadScoringRuleRequest } from "../types/api";

export interface ListLeadScoringRulesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listLeadScoringRules(params: ListLeadScoringRulesParams = {}): Promise<PageResponse<LeadScoringRuleDto>> {
  return unwrap(apiClient.get("/api/v1/lead-scoring-rules", { params }));
}

export function getLeadScoringRule(ruleId: string): Promise<LeadScoringRuleDto> {
  return unwrap(apiClient.get(`/api/v1/lead-scoring-rules/${ruleId}`));
}

export function createLeadScoringRule(request: CreateLeadScoringRuleRequest): Promise<LeadScoringRuleDto> {
  return unwrap(apiClient.post("/api/v1/lead-scoring-rules", request));
}

export function updateLeadScoringRule(ruleId: string, request: UpdateLeadScoringRuleRequest): Promise<LeadScoringRuleDto> {
  return unwrap(apiClient.put(`/api/v1/lead-scoring-rules/${ruleId}`, request));
}

export function deleteLeadScoringRule(ruleId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/lead-scoring-rules/${ruleId}`));
}
