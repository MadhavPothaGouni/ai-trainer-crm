import { apiClient, unwrap } from "../lib/apiClient";
import type {
  AuthResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  ResetPasswordRequest,
  VerifyEmailRequest,
} from "../types/api";

export function register(request: RegisterRequest): Promise<AuthResponse> {
  return unwrap(apiClient.post("/api/v1/auth/register", request));
}

export function login(request: LoginRequest): Promise<AuthResponse> {
  return unwrap(apiClient.post("/api/v1/auth/login", request));
}

export function logout(request: RefreshTokenRequest): Promise<null> {
  return unwrap(apiClient.post("/api/v1/auth/logout", request));
}

export function forgotPassword(request: ForgotPasswordRequest): Promise<null> {
  return unwrap(apiClient.post("/api/v1/auth/forgot-password", request));
}

export function resetPassword(request: ResetPasswordRequest): Promise<null> {
  return unwrap(apiClient.post("/api/v1/auth/reset-password", request));
}

export function verifyEmail(request: VerifyEmailRequest): Promise<null> {
  return unwrap(apiClient.post("/api/v1/auth/verify-email", request));
}

export function changePassword(request: ChangePasswordRequest): Promise<null> {
  return unwrap(apiClient.post("/api/v1/auth/change-password", request));
}
