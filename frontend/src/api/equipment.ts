import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateEquipmentRequest, EquipmentDto, PageResponse, UpdateEquipmentRequest } from "../types/api";

export interface ListEquipmentParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listEquipment(params: ListEquipmentParams = {}): Promise<PageResponse<EquipmentDto>> {
  return unwrap(apiClient.get("/api/v1/equipment", { params }));
}

export function getEquipment(equipmentId: string): Promise<EquipmentDto> {
  return unwrap(apiClient.get(`/api/v1/equipment/${equipmentId}`));
}

export function createEquipment(request: CreateEquipmentRequest): Promise<EquipmentDto> {
  return unwrap(apiClient.post("/api/v1/equipment", request));
}

export function updateEquipment(equipmentId: string, request: UpdateEquipmentRequest): Promise<EquipmentDto> {
  return unwrap(apiClient.put(`/api/v1/equipment/${equipmentId}`, request));
}

export function deleteEquipment(equipmentId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/equipment/${equipmentId}`));
}
