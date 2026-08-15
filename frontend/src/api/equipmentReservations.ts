import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateEquipmentReservationRequest,
  EquipmentReservationDto,
  PageResponse,
  UpdateEquipmentReservationRequest,
  UpdateEquipmentReservationStatusRequest,
} from "../types/api";

export interface ListEquipmentReservationsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listEquipmentReservations(params: ListEquipmentReservationsParams = {}): Promise<PageResponse<EquipmentReservationDto>> {
  return unwrap(apiClient.get("/api/v1/equipment-reservations", { params }));
}

export function getEquipmentReservation(equipmentReservationId: string): Promise<EquipmentReservationDto> {
  return unwrap(apiClient.get(`/api/v1/equipment-reservations/${equipmentReservationId}`));
}

export function createEquipmentReservation(request: CreateEquipmentReservationRequest): Promise<EquipmentReservationDto> {
  return unwrap(apiClient.post("/api/v1/equipment-reservations", request));
}

export function updateEquipmentReservation(
  equipmentReservationId: string,
  request: UpdateEquipmentReservationRequest,
): Promise<EquipmentReservationDto> {
  return unwrap(apiClient.put(`/api/v1/equipment-reservations/${equipmentReservationId}`, request));
}

export function updateEquipmentReservationStatus(
  equipmentReservationId: string,
  request: UpdateEquipmentReservationStatusRequest,
): Promise<EquipmentReservationDto> {
  return unwrap(apiClient.patch(`/api/v1/equipment-reservations/${equipmentReservationId}/status`, request));
}

export function deleteEquipmentReservation(equipmentReservationId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/equipment-reservations/${equipmentReservationId}`));
}
