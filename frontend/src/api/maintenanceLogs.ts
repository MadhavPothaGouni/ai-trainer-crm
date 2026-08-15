import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateMaintenanceLogRequest, MaintenanceLogDto, PageResponse, UpdateMaintenanceLogRequest } from "../types/api";

export interface ListMaintenanceLogsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listMaintenanceLogs(params: ListMaintenanceLogsParams = {}): Promise<PageResponse<MaintenanceLogDto>> {
  return unwrap(apiClient.get("/api/v1/maintenance-logs", { params }));
}

export function getMaintenanceLog(maintenanceLogId: string): Promise<MaintenanceLogDto> {
  return unwrap(apiClient.get(`/api/v1/maintenance-logs/${maintenanceLogId}`));
}

export function createMaintenanceLog(request: CreateMaintenanceLogRequest): Promise<MaintenanceLogDto> {
  return unwrap(apiClient.post("/api/v1/maintenance-logs", request));
}

export function updateMaintenanceLog(maintenanceLogId: string, request: UpdateMaintenanceLogRequest): Promise<MaintenanceLogDto> {
  return unwrap(apiClient.put(`/api/v1/maintenance-logs/${maintenanceLogId}`, request));
}

export function deleteMaintenanceLog(maintenanceLogId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/maintenance-logs/${maintenanceLogId}`));
}
