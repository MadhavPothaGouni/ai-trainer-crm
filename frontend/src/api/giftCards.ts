import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateGiftCardRequest,
  GiftCardDto,
  PageResponse,
  RedeemGiftCardRequest,
  UpdateGiftCardRequest,
  UpdateGiftCardStatusRequest,
} from "../types/api";

export interface ListGiftCardsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listGiftCards(params: ListGiftCardsParams = {}): Promise<PageResponse<GiftCardDto>> {
  return unwrap(apiClient.get("/api/v1/gift-cards", { params }));
}

export function getGiftCard(giftCardId: string): Promise<GiftCardDto> {
  return unwrap(apiClient.get(`/api/v1/gift-cards/${giftCardId}`));
}

export function createGiftCard(request: CreateGiftCardRequest): Promise<GiftCardDto> {
  return unwrap(apiClient.post("/api/v1/gift-cards", request));
}

export function updateGiftCard(giftCardId: string, request: UpdateGiftCardRequest): Promise<GiftCardDto> {
  return unwrap(apiClient.put(`/api/v1/gift-cards/${giftCardId}`, request));
}

export function updateGiftCardStatus(giftCardId: string, request: UpdateGiftCardStatusRequest): Promise<GiftCardDto> {
  return unwrap(apiClient.patch(`/api/v1/gift-cards/${giftCardId}/status`, request));
}

export function redeemGiftCard(giftCardId: string, request: RedeemGiftCardRequest): Promise<GiftCardDto> {
  return unwrap(apiClient.post(`/api/v1/gift-cards/${giftCardId}/redeem`, request));
}

export function deleteGiftCard(giftCardId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/gift-cards/${giftCardId}`));
}
