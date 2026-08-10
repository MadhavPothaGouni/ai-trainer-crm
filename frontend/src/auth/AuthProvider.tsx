import { useCallback, useEffect, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import { getMyProfile } from "../api/users";
import { setSessionExpiredHandler } from "../lib/apiClient";
import type { LoginRequest, RegisterRequest, UserDto } from "../types/api";
import { AuthContext } from "./AuthContext";
import { clearTokens, loadTokens, saveTokens } from "./tokenStorage";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    let cancelled = false;

    // A stored access token can be stale (expired, or the server restarted
    // with a new JWT secret) - fetching /users/me both confirms the
    // session is still good and gives us the full profile in one request,
    // rather than trusting whatever AuthResponse said at login time.
    async function restoreSession() {
      if (!loadTokens()) {
        setIsInitializing(false);
        return;
      }
      try {
        const profile = await getMyProfile();
        if (!cancelled) setUser(profile);
      } catch {
        clearTokens();
        if (!cancelled) setUser(null);
      } finally {
        if (!cancelled) setIsInitializing(false);
      }
    }

    void restoreSession();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));
    return () => setSessionExpiredHandler(null);
  }, []);

  const login = useCallback(async (request: LoginRequest) => {
    const auth = await authApi.login(request);
    saveTokens({ accessToken: auth.accessToken, refreshToken: auth.refreshToken });
    setUser(await getMyProfile());
  }, []);

  const register = useCallback(async (request: RegisterRequest) => {
    const auth = await authApi.register(request);
    saveTokens({ accessToken: auth.accessToken, refreshToken: auth.refreshToken });
    setUser(await getMyProfile());
  }, []);

  const logout = useCallback(async () => {
    const tokens = loadTokens();
    clearTokens();
    setUser(null);
    if (tokens) {
      // Best-effort: the user is logged out client-side regardless of whether
      // this call succeeds, so a dead network shouldn't block logout.
      await authApi.logout({ refreshToken: tokens.refreshToken }).catch(() => undefined);
    }
  }, []);

  const refreshUser = useCallback(async () => {
    setUser(await getMyProfile());
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isInitializing,
        isAuthenticated: user !== null,
        login,
        register,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
