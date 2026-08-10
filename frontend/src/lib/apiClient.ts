import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import type { ApiErrorBody, ApiResponse, AuthResponse } from "../types/api";
import { clearTokens, loadTokens, saveTokens } from "../auth/tokenStorage";

// Relative by default so the Vite dev-server proxy (see vite.config.ts)
// handles it without any env setup; set VITE_API_BASE_URL for a prod build
// where the frontend is served from a different origin than the backend.
const baseURL = import.meta.env.VITE_API_BASE_URL ?? "";

export const apiClient = axios.create({ baseURL });

// Raised in place of AxiosError so callers (forms, pages) can read a single
// consistent shape regardless of whether the backend returned a structured
// ErrorResponse or the request failed before a response ever came back
// (network error, CORS, backend down).
export class ApiError extends Error {
  readonly errorCode: string;
  readonly status: number | null;
  readonly fieldErrors: ApiErrorBody["fieldErrors"];
  readonly traceId: string | null;

  constructor(body: Partial<ApiErrorBody> & { message: string }, status: number | null) {
    super(body.message);
    this.name = "ApiError";
    this.errorCode = body.errorCode ?? "UNKNOWN_ERROR";
    this.status = status;
    this.fieldErrors = body.fieldErrors ?? null;
    this.traceId = body.traceId ?? null;
  }
}

function toApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (body?.message) {
      return new ApiError(body, error.response?.status ?? null);
    }
    return new ApiError(
      { message: "Could not reach the server. Check your connection and try again." },
      error.response?.status ?? null,
    );
  }
  return new ApiError({ message: "Something unexpected went wrong." }, null);
}

/** Unwraps ApiResponse<T> to T, or throws ApiError. Every call site should go through this rather than reading .data directly. */
export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  try {
    const response = await promise;
    // data is only ever null on endpoints whose T is void/null by contract (logout, delete, ...);
    // callers of those don't await a value, so the cast is safe for every call site that does.
    return response.data.data as T;
  } catch (error) {
    throw toApiError(error);
  }
}

apiClient.interceptors.request.use((config) => {
  const tokens = loadTokens();
  if (tokens && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${tokens.accessToken}`;
  }
  return config;
});

// Requests tagged with this (the login/register/refresh calls themselves)
// never trigger the refresh-and-retry flow below - retrying a failed
// refresh call with itself would just loop forever.
declare module "axios" {
  interface AxiosRequestConfig {
    skipAuthRefresh?: boolean;
  }
}

let onSessionExpired: (() => void) | null = null;
/** AuthContext registers its logout function here on mount, so this module can react to a failed refresh without importing React/router state. */
export function setSessionExpiredHandler(handler: (() => void) | null): void {
  onSessionExpired = handler;
}

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const tokens = loadTokens();
  if (!tokens) return null;

  try {
    const response = await axios.post<ApiResponse<AuthResponse>>(
      `${baseURL}/api/v1/auth/refresh`,
      { refreshToken: tokens.refreshToken },
      { skipAuthRefresh: true },
    );
    const auth = response.data.data;
    if (!auth) return null;
    saveTokens({ accessToken: auth.accessToken, refreshToken: auth.refreshToken });
    return auth.accessToken;
  } catch {
    return null;
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

    if (
      error.response?.status !== 401 ||
      !originalRequest ||
      originalRequest.skipAuthRefresh ||
      originalRequest._retried
    ) {
      return Promise.reject(error);
    }

    originalRequest._retried = true;

    // Multiple requests can 401 at once (e.g. a page firing several
    // parallel calls right as the access token expires) - share one
    // in-flight refresh instead of racing several refresh calls, since the
    // refresh token itself rotates on use and a second call would fail
    // reuse detection against the first.
    if (!refreshPromise) {
      refreshPromise = refreshAccessToken().finally(() => {
        refreshPromise = null;
      });
    }

    const newAccessToken = await refreshPromise;
    if (!newAccessToken) {
      clearTokens();
      onSessionExpired?.();
      return Promise.reject(error);
    }

    originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
    return apiClient(originalRequest);
  },
);
