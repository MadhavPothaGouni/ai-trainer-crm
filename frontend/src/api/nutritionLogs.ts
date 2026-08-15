import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateNutritionLogRequest, NutritionLogDto, PageResponse, UpdateNutritionLogRequest } from "../types/api";

export interface ListNutritionLogsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listNutritionLogs(params: ListNutritionLogsParams = {}): Promise<PageResponse<NutritionLogDto>> {
  return unwrap(apiClient.get("/api/v1/nutrition-logs", { params }));
}

export function getNutritionLog(nutritionLogId: string): Promise<NutritionLogDto> {
  return unwrap(apiClient.get(`/api/v1/nutrition-logs/${nutritionLogId}`));
}

export function createNutritionLog(request: CreateNutritionLogRequest): Promise<NutritionLogDto> {
  return unwrap(apiClient.post("/api/v1/nutrition-logs", request));
}

export function updateNutritionLog(nutritionLogId: string, request: UpdateNutritionLogRequest): Promise<NutritionLogDto> {
  return unwrap(apiClient.put(`/api/v1/nutrition-logs/${nutritionLogId}`, request));
}

export function deleteNutritionLog(nutritionLogId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/nutrition-logs/${nutritionLogId}`));
}
