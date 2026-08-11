import { apiClient, unwrap } from "../lib/apiClient";
import type { ApiKeyDto, CreateApiKeyRequest, PageResponse } from "../types/api";

export interface ListApiKeysParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listApiKeys(params: ListApiKeysParams = {}): Promise<PageResponse<ApiKeyDto>> {
  return unwrap(apiClient.get("/api/v1/api-keys", { params }));
}

export function createApiKey(request: CreateApiKeyRequest): Promise<ApiKeyDto> {
  return unwrap(apiClient.post("/api/v1/api-keys", request));
}

export function revokeApiKey(apiKeyId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/api-keys/${apiKeyId}`));
}
