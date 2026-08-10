import { apiClient, unwrap } from "../lib/apiClient";
import type {
  AssignOwnerRequest,
  CreateOpportunityRequest,
  OpportunityDto,
  PageResponse,
  UpdateOpportunityRequest,
  UpdateOpportunityStageRequest,
} from "../types/api";

export interface ListOpportunitiesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listOpportunities(params: ListOpportunitiesParams = {}): Promise<PageResponse<OpportunityDto>> {
  return unwrap(apiClient.get("/api/v1/opportunities", { params }));
}

export function getOpportunity(opportunityId: string): Promise<OpportunityDto> {
  return unwrap(apiClient.get(`/api/v1/opportunities/${opportunityId}`));
}

export function createOpportunity(request: CreateOpportunityRequest): Promise<OpportunityDto> {
  return unwrap(apiClient.post("/api/v1/opportunities", request));
}

export function updateOpportunity(opportunityId: string, request: UpdateOpportunityRequest): Promise<OpportunityDto> {
  return unwrap(apiClient.put(`/api/v1/opportunities/${opportunityId}`, request));
}

export function updateOpportunityStage(
  opportunityId: string,
  request: UpdateOpportunityStageRequest,
): Promise<OpportunityDto> {
  return unwrap(apiClient.patch(`/api/v1/opportunities/${opportunityId}/stage`, request));
}

export function deleteOpportunity(opportunityId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/opportunities/${opportunityId}`));
}

export function assignOpportunityOwner(opportunityId: string, request: AssignOwnerRequest): Promise<OpportunityDto> {
  return unwrap(apiClient.patch(`/api/v1/opportunities/${opportunityId}/owner`, request));
}
