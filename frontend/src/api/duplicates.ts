import { apiClient, unwrap } from "../lib/apiClient";
import type { DuplicateEntityType, DuplicateMatchDto, DuplicateMatchStatus, MergeDuplicateRequest } from "../types/api";

export interface ListDuplicateMatchesParams {
  entityType: DuplicateEntityType;
  status?: DuplicateMatchStatus;
}

export function listDuplicateMatches(params: ListDuplicateMatchesParams): Promise<DuplicateMatchDto[]> {
  return unwrap(apiClient.get("/api/v1/duplicates", { params }));
}

export function getDuplicateMatch(matchId: string): Promise<DuplicateMatchDto> {
  return unwrap(apiClient.get(`/api/v1/duplicates/${matchId}`));
}

export function mergeDuplicateMatch(matchId: string, request: MergeDuplicateRequest): Promise<DuplicateMatchDto> {
  return unwrap(apiClient.post(`/api/v1/duplicates/${matchId}/merge`, request));
}

export function dismissDuplicateMatch(matchId: string): Promise<DuplicateMatchDto> {
  return unwrap(apiClient.post(`/api/v1/duplicates/${matchId}/dismiss`, {}));
}
