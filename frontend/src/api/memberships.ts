import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateMembershipRequest,
  MembershipDto,
  PageResponse,
  UpdateMembershipRequest,
  UpdateMembershipStatusRequest,
} from "../types/api";

export interface ListMembershipsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listMemberships(params: ListMembershipsParams = {}): Promise<PageResponse<MembershipDto>> {
  return unwrap(apiClient.get("/api/v1/memberships", { params }));
}

export function getMembership(membershipId: string): Promise<MembershipDto> {
  return unwrap(apiClient.get(`/api/v1/memberships/${membershipId}`));
}

export function createMembership(request: CreateMembershipRequest): Promise<MembershipDto> {
  return unwrap(apiClient.post("/api/v1/memberships", request));
}

export function updateMembership(membershipId: string, request: UpdateMembershipRequest): Promise<MembershipDto> {
  return unwrap(apiClient.put(`/api/v1/memberships/${membershipId}`, request));
}

export function updateMembershipStatus(membershipId: string, request: UpdateMembershipStatusRequest): Promise<MembershipDto> {
  return unwrap(apiClient.patch(`/api/v1/memberships/${membershipId}/status`, request));
}

export function deleteMembership(membershipId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/memberships/${membershipId}`));
}
