import { createContext } from "react";
import type { LoginRequest, RegisterRequest, UserDto } from "../types/api";

export interface AuthContextValue {
  user: UserDto | null;
  /** True only while the initial "do we already have a session" check is running, on first load. */
  isInitializing: boolean;
  isAuthenticated: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  /** Re-fetches /users/me - call after anything that can change the current user (profile edit, role change). */
  refreshUser: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
