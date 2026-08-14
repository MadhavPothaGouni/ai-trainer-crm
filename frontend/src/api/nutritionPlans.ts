import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateNutritionPlanRequest,
  NutritionPlanDto,
  PageResponse,
  UpdateNutritionPlanRequest,
  UpdateNutritionPlanStatusRequest,
} from "../types/api";

export interface ListNutritionPlansParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listNutritionPlans(params: ListNutritionPlansParams = {}): Promise<PageResponse<NutritionPlanDto>> {
  return unwrap(apiClient.get("/api/v1/nutrition-plans", { params }));
}

export function getNutritionPlan(nutritionPlanId: string): Promise<NutritionPlanDto> {
  return unwrap(apiClient.get(`/api/v1/nutrition-plans/${nutritionPlanId}`));
}

export function createNutritionPlan(request: CreateNutritionPlanRequest): Promise<NutritionPlanDto> {
  return unwrap(apiClient.post("/api/v1/nutrition-plans", request));
}

export function updateNutritionPlan(nutritionPlanId: string, request: UpdateNutritionPlanRequest): Promise<NutritionPlanDto> {
  return unwrap(apiClient.put(`/api/v1/nutrition-plans/${nutritionPlanId}`, request));
}

export function updateNutritionPlanStatus(
  nutritionPlanId: string,
  request: UpdateNutritionPlanStatusRequest,
): Promise<NutritionPlanDto> {
  return unwrap(apiClient.patch(`/api/v1/nutrition-plans/${nutritionPlanId}/status`, request));
}

export function deleteNutritionPlan(nutritionPlanId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/nutrition-plans/${nutritionPlanId}`));
}
