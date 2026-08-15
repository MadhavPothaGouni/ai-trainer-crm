import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateMembershipPlanRequest, MembershipPlanDto, PageResponse, UpdateMembershipPlanRequest } from "../types/api";

export interface ListMembershipPlansParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listMembershipPlans(params: ListMembershipPlansParams = {}): Promise<PageResponse<MembershipPlanDto>> {
  return unwrap(apiClient.get("/api/v1/membership-plans", { params }));
}

export function getMembershipPlan(membershipPlanId: string): Promise<MembershipPlanDto> {
  return unwrap(apiClient.get(`/api/v1/membership-plans/${membershipPlanId}`));
}

export function createMembershipPlan(request: CreateMembershipPlanRequest): Promise<MembershipPlanDto> {
  return unwrap(apiClient.post("/api/v1/membership-plans", request));
}

export function updateMembershipPlan(membershipPlanId: string, request: UpdateMembershipPlanRequest): Promise<MembershipPlanDto> {
  return unwrap(apiClient.put(`/api/v1/membership-plans/${membershipPlanId}`, request));
}

export function deleteMembershipPlan(membershipPlanId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/membership-plans/${membershipPlanId}`));
}
