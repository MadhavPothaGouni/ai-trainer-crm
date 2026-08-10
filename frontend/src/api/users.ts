import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateUserRequest,
  PageResponse,
  UpdateProfileRequest,
  UpdateUserRolesRequest,
  UpdateUserStatusRequest,
  UserDto,
} from "../types/api";

export interface ListUsersParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listUsers(params: ListUsersParams = {}): Promise<PageResponse<UserDto>> {
  return unwrap(apiClient.get("/api/v1/users", { params }));
}

export function getMyProfile(): Promise<UserDto> {
  return unwrap(apiClient.get("/api/v1/users/me"));
}

export function updateMyProfile(request: UpdateProfileRequest): Promise<UserDto> {
  return unwrap(apiClient.patch("/api/v1/users/me", request));
}

export function getUser(userId: string): Promise<UserDto> {
  return unwrap(apiClient.get(`/api/v1/users/${userId}`));
}

export function inviteUser(request: CreateUserRequest): Promise<UserDto> {
  return unwrap(apiClient.post("/api/v1/users", request));
}

export function updateUserRoles(userId: string, request: UpdateUserRolesRequest): Promise<UserDto> {
  return unwrap(apiClient.patch(`/api/v1/users/${userId}/roles`, request));
}

export function updateUserStatus(userId: string, request: UpdateUserStatusRequest): Promise<UserDto> {
  return unwrap(apiClient.patch(`/api/v1/users/${userId}/status`, request));
}

export function removeUser(userId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/users/${userId}`));
}
