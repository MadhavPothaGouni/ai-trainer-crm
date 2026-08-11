import { apiClient, unwrap } from "../lib/apiClient";
import type { LeadFunnelStageDto, PipelineStageSummaryDto, RepLeaderboardEntryDto } from "../types/api";

export function getPipelineByStage(): Promise<PipelineStageSummaryDto[]> {
  return unwrap(apiClient.get("/api/v1/reports/pipeline-by-stage"));
}

export function getLeadFunnel(): Promise<LeadFunnelStageDto[]> {
  return unwrap(apiClient.get("/api/v1/reports/lead-funnel"));
}

export function getLeaderboard(): Promise<RepLeaderboardEntryDto[]> {
  return unwrap(apiClient.get("/api/v1/reports/leaderboard"));
}
