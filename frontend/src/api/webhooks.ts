import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateWebhookSubscriptionRequest, PageResponse, UpdateWebhookSubscriptionRequest, WebhookSubscriptionDto } from "../types/api";

export interface ListWebhooksParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listWebhooks(params: ListWebhooksParams = {}): Promise<PageResponse<WebhookSubscriptionDto>> {
  return unwrap(apiClient.get("/api/v1/webhooks", { params }));
}

export function createWebhook(request: CreateWebhookSubscriptionRequest): Promise<WebhookSubscriptionDto> {
  return unwrap(apiClient.post("/api/v1/webhooks", request));
}

export function updateWebhook(webhookId: string, request: UpdateWebhookSubscriptionRequest): Promise<WebhookSubscriptionDto> {
  return unwrap(apiClient.put(`/api/v1/webhooks/${webhookId}`, request));
}

export function deleteWebhook(webhookId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/webhooks/${webhookId}`));
}
