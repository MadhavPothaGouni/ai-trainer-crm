import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateMembershipFreezeRequest,
  MembershipFreezeDto,
  PageResponse,
  UpdateMembershipFreezeRequest,
  UpdateMembershipFreezeStatusRequest,
} from "../types/api";

export interface ListMembershipFreezesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listMembershipFreezes(params: ListMembershipFreezesParams = {}): Promise<PageResponse<MembershipFreezeDto>> {
  return unwrap(apiClient.get("/api/v1/membership-freezes", { params }));
}

export function getMembershipFreeze(membershipFreezeId: string): Promise<MembershipFreezeDto> {
  return unwrap(apiClient.get(`/api/v1/membership-freezes/${membershipFreezeId}`));
}

export function createMembershipFreeze(request: CreateMembershipFreezeRequest): Promise<MembershipFreezeDto> {
  return unwrap(apiClient.post("/api/v1/membership-freezes", request));
}

export function updateMembershipFreeze(membershipFreezeId: string, request: UpdateMembershipFreezeRequest): Promise<MembershipFreezeDto> {
  return unwrap(apiClient.put(`/api/v1/membership-freezes/${membershipFreezeId}`, request));
}

export function updateMembershipFreezeStatus(
  membershipFreezeId: string,
  request: UpdateMembershipFreezeStatusRequest,
): Promise<MembershipFreezeDto> {
  return unwrap(apiClient.patch(`/api/v1/membership-freezes/${membershipFreezeId}/status`, request));
}

export function deleteMembershipFreeze(membershipFreezeId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/membership-freezes/${membershipFreezeId}`));
}
