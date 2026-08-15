import { apiClient, unwrap } from "../lib/apiClient";
import type { CreatePromoRedemptionRequest, PageResponse, PromoRedemptionDto, UpdatePromoRedemptionRequest } from "../types/api";

export interface ListPromoRedemptionsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listPromoRedemptions(params: ListPromoRedemptionsParams = {}): Promise<PageResponse<PromoRedemptionDto>> {
  return unwrap(apiClient.get("/api/v1/promo-redemptions", { params }));
}

export function getPromoRedemption(promoRedemptionId: string): Promise<PromoRedemptionDto> {
  return unwrap(apiClient.get(`/api/v1/promo-redemptions/${promoRedemptionId}`));
}

export function createPromoRedemption(request: CreatePromoRedemptionRequest): Promise<PromoRedemptionDto> {
  return unwrap(apiClient.post("/api/v1/promo-redemptions", request));
}

export function updatePromoRedemption(promoRedemptionId: string, request: UpdatePromoRedemptionRequest): Promise<PromoRedemptionDto> {
  return unwrap(apiClient.put(`/api/v1/promo-redemptions/${promoRedemptionId}`, request));
}

export function deletePromoRedemption(promoRedemptionId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/promo-redemptions/${promoRedemptionId}`));
}
