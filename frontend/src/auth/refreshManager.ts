import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import axios from 'axios';
import type { TokenResponse } from '../api/types';
import { getRefreshToken, restoreRefreshToken } from './authState';

let sharedRefreshPromise: Promise<TokenResponse> | null = null;
let refreshHandler: ((refreshToken: string) => Promise<TokenResponse>) | null = null;
let failedHandler: (() => void) | null = null;

export function configureRefreshHandlers(options: {
  refresh: (refreshToken: string) => Promise<TokenResponse>;
  onFailure: () => void;
}): void {
  refreshHandler = options.refresh;
  failedHandler = options.onFailure;
}

export function clearRefreshHandlers(): void {
  refreshHandler = null;
  failedHandler = null;
}

export function getSharedRefreshPromise(): Promise<TokenResponse> | null {
  return sharedRefreshPromise;
}

export function requestSharedRefresh(refreshToken: string): Promise<TokenResponse> {
  if (sharedRefreshPromise) return sharedRefreshPromise;
  if (!refreshHandler) {
    return Promise.reject(new Error('Authentication refresh is not configured.'));
  }

  sharedRefreshPromise = refreshHandler(refreshToken)
    .finally(() => {
      sharedRefreshPromise = null;
    });
  return sharedRefreshPromise;
}

export function resetSharedRefresh(): void {
  sharedRefreshPromise = null;
}

export function isAuthExclusion(url: string | undefined): boolean {
  if (!url) return false;
  const path = url.split('?')[0];
  return /^\/api\/auth\/(login|register|refresh|logout)(?:\/|$)/.test(path);
}

export function installRefreshInterceptor(client: AxiosInstance): void {
  client.interceptors.response.use(
    (response: AxiosResponse) => response,
    async (error: unknown) => {
      if (!axios.isAxiosError(error) || error.response?.status !== 401) {
        throw error;
      }

      const original = error.config as (InternalAxiosRequestConfig & { _authRetry?: boolean }) | undefined;
      if (!original || original._authRetry || isAuthExclusion(original.url)) throw error;

      original._authRetry = true;
      const inMemoryRefreshToken = getRefreshToken() ?? restoreRefreshToken();

      if (!inMemoryRefreshToken) {
        failedHandler?.();
        throw error;
      }

      try {
        await requestSharedRefresh(inMemoryRefreshToken);
        return client(original);
      } catch {
        failedHandler?.();
        throw error;
      }
    },
  );
}