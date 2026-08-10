// The backend hands back both tokens as plain JSON fields (see AuthResponse)
// rather than as an httpOnly Set-Cookie - it's a pure JSON API with no
// server-side session, so there's no cookie for the browser to hold onto
// for us. That means persisting the refresh token here, in localStorage, is
// the only way a page reload doesn't force a fresh login. This is the usual
// trade-off for token-in-body APIs (XSS could read it) rather than a
// mistake; the access token is short-lived (15 min) and refresh tokens are
// rotated + reuse-detected server-side (see RefreshTokenRepository), which
// bounds the blast radius of a leaked token.

const ACCESS_TOKEN_KEY = "crm.accessToken";
const REFRESH_TOKEN_KEY = "crm.refreshToken";

export interface StoredTokens {
  accessToken: string;
  refreshToken: string;
}

export function loadTokens(): StoredTokens | null {
  const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY);
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!accessToken || !refreshToken) return null;
  return { accessToken, refreshToken };
}

export function saveTokens(tokens: StoredTokens): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}
