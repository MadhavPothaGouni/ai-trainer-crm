// Mirrors the backend's response envelopes exactly (see
// backend/crm-platform/.../common/dto/{ApiResponse,ErrorResponse,PageResponse}.java).
// Keeping these in one file makes it obvious when the frontend's assumptions
// about the wire format drift from what the backend actually sends.

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  timestamp: string;
}

export interface FieldError {
  field: string;
  message: string;
  rejectedValue: unknown;
}

export interface ApiErrorBody {
  success: false;
  errorCode: string;
  message: string;
  status: number;
  path: string;
  timestamp: string;
  fieldErrors: FieldError[] | null;
  traceId: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ---- Auth ----

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  organizationName?: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  fullName: string;
}

// ---- Organization ----

export interface OrganizationDto {
  id: string;
  name: string;
  slug: string;
  defaultCurrency: string;
  timezone: string;
  fiscalYearStartMonth: number;
}

export interface UpdateOrganizationRequest {
  name: string;
  defaultCurrency?: string;
  timezone?: string;
  fiscalYearStartMonth: number;
}

// ---- User ----

export type UserStatus = "PENDING_VERIFICATION" | "ACTIVE" | "SUSPENDED" | "DEACTIVATED";

export interface UserDto {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  phone: string | null;
  avatarUrl: string | null;
  status: UserStatus;
  emailVerified: boolean;
  mfaEnabled: boolean;
  teamId: string | null;
  managerId: string | null;
  roles: string[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  phone?: string;
  timezone?: string;
  locale?: string;
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  roleIds?: string[] | null;
}

export interface UpdateUserRolesRequest {
  roleIds: string[];
}

export interface UpdateUserStatusRequest {
  status: UserStatus;
}

// ---- Role / Permission ----

export interface PermissionDto {
  id: string;
  resource: string;
  action: string;
  scope: string;
  description: string;
  authorityName: string;
}

export interface RoleDto {
  id: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  permissions: PermissionDto[];
}

export interface CreateRoleRequest {
  name: string;
  description?: string;
  permissionIds?: string[] | null;
}

export interface UpdateRoleRequest {
  name: string;
  description?: string;
  permissionIds?: string[] | null;
}
