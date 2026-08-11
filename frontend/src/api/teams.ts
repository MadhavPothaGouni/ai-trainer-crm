import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateTeamRequest, PageResponse, TeamDto, UpdateTeamRequest } from "../types/api";

export interface ListTeamsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listTeams(params: ListTeamsParams = {}): Promise<PageResponse<TeamDto>> {
  return unwrap(apiClient.get("/api/v1/teams", { params }));
}

export function getTeam(teamId: string): Promise<TeamDto> {
  return unwrap(apiClient.get(`/api/v1/teams/${teamId}`));
}

export function createTeam(request: CreateTeamRequest): Promise<TeamDto> {
  return unwrap(apiClient.post("/api/v1/teams", request));
}

export function updateTeam(teamId: string, request: UpdateTeamRequest): Promise<TeamDto> {
  return unwrap(apiClient.put(`/api/v1/teams/${teamId}`, request));
}

export function deleteTeam(teamId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/teams/${teamId}`));
}
