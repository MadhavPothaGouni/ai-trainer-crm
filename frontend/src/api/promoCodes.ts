import { apiClient, unwrap } from "../lib/apiClient";
import type { CreatePromoCodeRequest, PageResponse, PromoCodeDto, UpdatePromoCodeRequest } from "../types/api";

export interface ListPromoCodesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listPromoCodes(params: ListPromoCodesParams = {}): Promise<PageResponse<PromoCodeDto>> {
  return unwrap(apiClient.get("/api/v1/promo-codes", { params }));
}

export function getPromoCode(promoCodeId: string): Promise<PromoCodeDto> {
  return unwrap(apiClient.get(`/api/v1/promo-codes/${promoCodeId}`));
}

export function createPromoCode(request: CreatePromoCodeRequest): Promise<PromoCodeDto> {
  return unwrap(apiClient.post("/api/v1/promo-codes", request));
}

export function updatePromoCode(promoCodeId: string, request: UpdatePromoCodeRequest): Promise<PromoCodeDto> {
  return unwrap(apiClient.put(`/api/v1/promo-codes/${promoCodeId}`, request));
}

export function deletePromoCode(promoCodeId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/promo-codes/${promoCodeId}`));
}
