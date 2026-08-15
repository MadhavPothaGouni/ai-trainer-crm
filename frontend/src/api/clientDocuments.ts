import { apiClient, unwrap } from "../lib/apiClient";
import type {
  ClientDocumentDto,
  CreateClientDocumentRequest,
  PageResponse,
  UpdateClientDocumentRequest,
  UpdateClientDocumentStatusRequest,
} from "../types/api";

export interface ListClientDocumentsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listClientDocuments(params: ListClientDocumentsParams = {}): Promise<PageResponse<ClientDocumentDto>> {
  return unwrap(apiClient.get("/api/v1/client-documents", { params }));
}

export function getClientDocument(clientDocumentId: string): Promise<ClientDocumentDto> {
  return unwrap(apiClient.get(`/api/v1/client-documents/${clientDocumentId}`));
}

export function createClientDocument(request: CreateClientDocumentRequest): Promise<ClientDocumentDto> {
  return unwrap(apiClient.post("/api/v1/client-documents", request));
}

export function updateClientDocument(clientDocumentId: string, request: UpdateClientDocumentRequest): Promise<ClientDocumentDto> {
  return unwrap(apiClient.put(`/api/v1/client-documents/${clientDocumentId}`, request));
}

export function updateClientDocumentStatus(clientDocumentId: string, request: UpdateClientDocumentStatusRequest): Promise<ClientDocumentDto> {
  return unwrap(apiClient.patch(`/api/v1/client-documents/${clientDocumentId}/status`, request));
}

export function deleteClientDocument(clientDocumentId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/client-documents/${clientDocumentId}`));
}
