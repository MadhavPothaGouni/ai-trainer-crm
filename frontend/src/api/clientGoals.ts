import { apiClient, unwrap } from "../lib/apiClient";
import type {
  ClientGoalDto,
  CreateClientGoalRequest,
  PageResponse,
  UpdateClientGoalRequest,
  UpdateClientGoalStatusRequest,
} from "../types/api";

export interface ListClientGoalsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listClientGoals(params: ListClientGoalsParams = {}): Promise<PageResponse<ClientGoalDto>> {
  return unwrap(apiClient.get("/api/v1/client-goals", { params }));
}

export function getClientGoal(clientGoalId: string): Promise<ClientGoalDto> {
  return unwrap(apiClient.get(`/api/v1/client-goals/${clientGoalId}`));
}

export function createClientGoal(request: CreateClientGoalRequest): Promise<ClientGoalDto> {
  return unwrap(apiClient.post("/api/v1/client-goals", request));
}

export function updateClientGoal(clientGoalId: string, request: UpdateClientGoalRequest): Promise<ClientGoalDto> {
  return unwrap(apiClient.put(`/api/v1/client-goals/${clientGoalId}`, request));
}

export function updateClientGoalStatus(clientGoalId: string, request: UpdateClientGoalStatusRequest): Promise<ClientGoalDto> {
  return unwrap(apiClient.patch(`/api/v1/client-goals/${clientGoalId}/status`, request));
}

export function deleteClientGoal(clientGoalId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/client-goals/${clientGoalId}`));
}
