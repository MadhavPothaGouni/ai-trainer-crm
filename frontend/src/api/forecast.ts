import { apiClient, unwrap } from "../lib/apiClient";
import type { PipelineSnapshotDto, PipelineTrendPointDto } from "../types/api";

export interface ForecastRangeParams {
  from: string;
  to: string;
}

export function getPipelineSnapshots(params: ForecastRangeParams): Promise<PipelineSnapshotDto[]> {
  return unwrap(apiClient.get("/api/v1/forecast/snapshots", { params }));
}

export function getPipelineTrend(params: ForecastRangeParams): Promise<PipelineTrendPointDto[]> {
  return unwrap(apiClient.get("/api/v1/forecast/trend", { params }));
}
