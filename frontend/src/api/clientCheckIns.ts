import { apiClient, unwrap } from "../lib/apiClient";
import type {
  ClientCheckInDto,
  CreateClientCheckInRequest,
  PageResponse,
  UpdateClientCheckInRequest,
  UpdateClientCheckInStatusRequest,
} from "../types/api";

export interface ListClientCheckInsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listClientCheckIns(params: ListClientCheckInsParams = {}): Promise<PageResponse<ClientCheckInDto>> {
  return unwrap(apiClient.get("/api/v1/client-check-ins", { params }));
}

export function getClientCheckIn(clientCheckInId: string): Promise<ClientCheckInDto> {
  return unwrap(apiClient.get(`/api/v1/client-check-ins/${clientCheckInId}`));
}

export function createClientCheckIn(request: CreateClientCheckInRequest): Promise<ClientCheckInDto> {
  return unwrap(apiClient.post("/api/v1/client-check-ins", request));
}

export function updateClientCheckIn(clientCheckInId: string, request: UpdateClientCheckInRequest): Promise<ClientCheckInDto> {
  return unwrap(apiClient.put(`/api/v1/client-check-ins/${clientCheckInId}`, request));
}

export function updateClientCheckInStatus(
  clientCheckInId: string,
  request: UpdateClientCheckInStatusRequest,
): Promise<ClientCheckInDto> {
  return unwrap(apiClient.patch(`/api/v1/client-check-ins/${clientCheckInId}/status`, request));
}

export function deleteClientCheckIn(clientCheckInId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/client-check-ins/${clientCheckInId}`));
}
