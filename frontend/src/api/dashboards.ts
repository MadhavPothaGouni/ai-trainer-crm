import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateDashboardRequest,
  CreateDashboardWidgetRequest,
  DashboardDataDto,
  DashboardDto,
  DashboardWidgetDto,
  PageResponse,
  UpdateDashboardRequest,
  UpdateDashboardWidgetRequest,
} from "../types/api";

export interface ListParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listDashboards(params: ListParams = {}): Promise<PageResponse<DashboardDto>> {
  return unwrap(apiClient.get("/api/v1/dashboards", { params }));
}

export function getDashboard(dashboardId: string): Promise<DashboardDto> {
  return unwrap(apiClient.get(`/api/v1/dashboards/${dashboardId}`));
}

export function getDashboardData(dashboardId: string): Promise<DashboardDataDto> {
  return unwrap(apiClient.get(`/api/v1/dashboards/${dashboardId}/data`));
}

export function createDashboard(request: CreateDashboardRequest): Promise<DashboardDto> {
  return unwrap(apiClient.post("/api/v1/dashboards", request));
}

export function updateDashboard(dashboardId: string, request: UpdateDashboardRequest): Promise<DashboardDto> {
  return unwrap(apiClient.put(`/api/v1/dashboards/${dashboardId}`, request));
}

export function setDashboardDefault(dashboardId: string): Promise<DashboardDto> {
  return unwrap(apiClient.post(`/api/v1/dashboards/${dashboardId}/default`, {}));
}

export function deleteDashboard(dashboardId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/dashboards/${dashboardId}`));
}

export function addDashboardWidget(dashboardId: string, request: CreateDashboardWidgetRequest): Promise<DashboardWidgetDto> {
  return unwrap(apiClient.post(`/api/v1/dashboards/${dashboardId}/widgets`, request));
}

export function updateDashboardWidget(
  dashboardId: string,
  widgetId: string,
  request: UpdateDashboardWidgetRequest,
): Promise<DashboardWidgetDto> {
  return unwrap(apiClient.put(`/api/v1/dashboards/${dashboardId}/widgets/${widgetId}`, request));
}

export function removeDashboardWidget(dashboardId: string, widgetId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/dashboards/${dashboardId}/widgets/${widgetId}`));
}
