import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateRegionRequest, RegionDto, RegionRollupDto, UpdateRegionRequest } from "../types/api";

export function listRegions(): Promise<RegionDto[]> {
  return unwrap(apiClient.get("/api/v1/regions"));
}

export function getRegion(regionId: string): Promise<RegionDto> {
  return unwrap(apiClient.get(`/api/v1/regions/${regionId}`));
}

export function getRegionRollup(regionId: string): Promise<RegionRollupDto> {
  return unwrap(apiClient.get(`/api/v1/regions/${regionId}/rollup`));
}

export function createRegion(request: CreateRegionRequest): Promise<RegionDto> {
  return unwrap(apiClient.post("/api/v1/regions", request));
}

export function updateRegion(regionId: string, request: UpdateRegionRequest): Promise<RegionDto> {
  return unwrap(apiClient.put(`/api/v1/regions/${regionId}`, request));
}

export function deleteRegion(regionId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/regions/${regionId}`));
}
