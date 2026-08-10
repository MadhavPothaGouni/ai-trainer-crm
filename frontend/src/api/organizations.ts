import { apiClient, unwrap } from "../lib/apiClient";
import type { OrganizationDto, UpdateOrganizationRequest } from "../types/api";

export function getMyOrganization(): Promise<OrganizationDto> {
  return unwrap(apiClient.get("/api/v1/organizations/me"));
}

export function updateMyOrganization(request: UpdateOrganizationRequest): Promise<OrganizationDto> {
  return unwrap(apiClient.patch("/api/v1/organizations/me", request));
}
