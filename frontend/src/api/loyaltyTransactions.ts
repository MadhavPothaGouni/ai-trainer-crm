import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateLoyaltyTransactionRequest,
  LoyaltyBalanceDto,
  LoyaltyTransactionDto,
  PageResponse,
  UpdateLoyaltyTransactionRequest,
} from "../types/api";

export interface ListLoyaltyTransactionsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listLoyaltyTransactions(params: ListLoyaltyTransactionsParams = {}): Promise<PageResponse<LoyaltyTransactionDto>> {
  return unwrap(apiClient.get("/api/v1/loyalty-transactions", { params }));
}

export function getLoyaltyTransaction(loyaltyTransactionId: string): Promise<LoyaltyTransactionDto> {
  return unwrap(apiClient.get(`/api/v1/loyalty-transactions/${loyaltyTransactionId}`));
}

export function getLoyaltyBalance(contactId: string): Promise<LoyaltyBalanceDto> {
  return unwrap(apiClient.get(`/api/v1/loyalty-transactions/balance/${contactId}`));
}

export function createLoyaltyTransaction(request: CreateLoyaltyTransactionRequest): Promise<LoyaltyTransactionDto> {
  return unwrap(apiClient.post("/api/v1/loyalty-transactions", request));
}

export function updateLoyaltyTransaction(loyaltyTransactionId: string, request: UpdateLoyaltyTransactionRequest): Promise<LoyaltyTransactionDto> {
  return unwrap(apiClient.put(`/api/v1/loyalty-transactions/${loyaltyTransactionId}`, request));
}

export function deleteLoyaltyTransaction(loyaltyTransactionId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/loyalty-transactions/${loyaltyTransactionId}`));
}
