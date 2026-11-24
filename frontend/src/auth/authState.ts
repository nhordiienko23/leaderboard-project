export const REFRESH_TOKEN_STORAGE_KEY = 'leaderboard.refreshToken';

let refreshTokenInMemory: string | null = null;

export function getRefreshToken(): string | null {
  return refreshTokenInMemory;
}

export function setRefreshToken(token: string | null): void {
  refreshTokenInMemory = token;
  if (token) {
    sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, token);
  } else {
    sessionStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  }
}

export function restoreRefreshToken(): string | null {
  const stored = sessionStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
  refreshTokenInMemory = stored;
  return stored;
}

export function clearRefreshToken(): void {
  refreshTokenInMemory = null;
  sessionStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
}

