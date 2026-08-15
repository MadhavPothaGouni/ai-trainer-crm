import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateReferralRequest,
  PageResponse,
  ReferralDto,
  UpdateReferralRequest,
  UpdateReferralStatusRequest,
} from "../types/api";

export interface ListReferralsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listReferrals(params: ListReferralsParams = {}): Promise<PageResponse<ReferralDto>> {
  return unwrap(apiClient.get("/api/v1/referrals", { params }));
}

export function getReferral(referralId: string): Promise<ReferralDto> {
  return unwrap(apiClient.get(`/api/v1/referrals/${referralId}`));
}

export function createReferral(request: CreateReferralRequest): Promise<ReferralDto> {
  return unwrap(apiClient.post("/api/v1/referrals", request));
}

export function updateReferral(referralId: string, request: UpdateReferralRequest): Promise<ReferralDto> {
  return unwrap(apiClient.put(`/api/v1/referrals/${referralId}`, request));
}

export function updateReferralStatus(referralId: string, request: UpdateReferralStatusRequest): Promise<ReferralDto> {
  return unwrap(apiClient.patch(`/api/v1/referrals/${referralId}/status`, request));
}

export function issueReferralReward(referralId: string): Promise<ReferralDto> {
  return unwrap(apiClient.patch(`/api/v1/referrals/${referralId}/reward`, {}));
}

export function deleteReferral(referralId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/referrals/${referralId}`));
}
