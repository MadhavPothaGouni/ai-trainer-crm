import { apiClient, unwrap } from "../lib/apiClient";
import type { AccountDto, AssignOwnerRequest, CreateAccountRequest, PageResponse, UpdateAccountRequest } from "../types/api";

export interface ListAccountsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listAccounts(params: ListAccountsParams = {}): Promise<PageResponse<AccountDto>> {
  return unwrap(apiClient.get("/api/v1/accounts", { params }));
}

export function getAccount(accountId: string): Promise<AccountDto> {
  return unwrap(apiClient.get(`/api/v1/accounts/${accountId}`));
}

export function createAccount(request: CreateAccountRequest): Promise<AccountDto> {
  return unwrap(apiClient.post("/api/v1/accounts", request));
}

export function updateAccount(accountId: string, request: UpdateAccountRequest): Promise<AccountDto> {
  return unwrap(apiClient.put(`/api/v1/accounts/${accountId}`, request));
}

export function deleteAccount(accountId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/accounts/${accountId}`));
}

export function assignAccountOwner(accountId: string, request: AssignOwnerRequest): Promise<AccountDto> {
  return unwrap(apiClient.patch(`/api/v1/accounts/${accountId}/owner`, request));
}
