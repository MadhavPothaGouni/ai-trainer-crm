import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateRoleRequest, PermissionDto, RoleDto, UpdateRoleRequest } from "../types/api";

export function listRoles(): Promise<RoleDto[]> {
  return unwrap(apiClient.get("/api/v1/roles"));
}

export function getRole(roleId: string): Promise<RoleDto> {
  return unwrap(apiClient.get(`/api/v1/roles/${roleId}`));
}

export function createRole(request: CreateRoleRequest): Promise<RoleDto> {
  return unwrap(apiClient.post("/api/v1/roles", request));
}

export function updateRole(roleId: string, request: UpdateRoleRequest): Promise<RoleDto> {
  return unwrap(apiClient.put(`/api/v1/roles/${roleId}`, request));
}

export function deleteRole(roleId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/roles/${roleId}`));
}

export function listPermissions(): Promise<PermissionDto[]> {
  return unwrap(apiClient.get("/api/v1/roles/permissions"));
}
