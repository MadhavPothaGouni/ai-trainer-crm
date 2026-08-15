import { apiClient, unwrap } from "../lib/apiClient";
import type { ClientFeedbackDto, CreateClientFeedbackRequest, PageResponse, UpdateClientFeedbackRequest } from "../types/api";

export interface ListClientFeedbackParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listClientFeedback(params: ListClientFeedbackParams = {}): Promise<PageResponse<ClientFeedbackDto>> {
  return unwrap(apiClient.get("/api/v1/client-feedback", { params }));
}

export function getClientFeedback(clientFeedbackId: string): Promise<ClientFeedbackDto> {
  return unwrap(apiClient.get(`/api/v1/client-feedback/${clientFeedbackId}`));
}

export function createClientFeedback(request: CreateClientFeedbackRequest): Promise<ClientFeedbackDto> {
  return unwrap(apiClient.post("/api/v1/client-feedback", request));
}

export function updateClientFeedback(clientFeedbackId: string, request: UpdateClientFeedbackRequest): Promise<ClientFeedbackDto> {
  return unwrap(apiClient.put(`/api/v1/client-feedback/${clientFeedbackId}`, request));
}

export function deleteClientFeedback(clientFeedbackId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/client-feedback/${clientFeedbackId}`));
}
