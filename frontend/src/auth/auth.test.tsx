import axios, { type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { act, render, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as authApi from '../api/auth';
import { apiClient, setAccessToken } from '../api/client';
import { clearRefreshToken, setRefreshToken, getRefreshToken } from './authState';
import { AuthProvider, useAuth } from './AuthContext';
import { parseJwtClaims } from './jwt';
import { configureRefreshHandlers, requestSharedRefresh, resetSharedRefresh, isAuthExclusion } from './refreshManager';
import { AdminRoute, GuestRoute, ProtectedRoute } from '../router/guards';
import { StrictMode } from 'react';

function jwt(payload: Record<string, unknown>): string {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none' })}.${encode(payload)}.signature`;
}
function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}
beforeEach(() => {
  vi.restoreAllMocks();
  sessionStorage.clear();
  setAccessToken(null);
  clearRefreshToken();
  resetSharedRefresh();
});
describe('JWT parsing', () => {
  it('parses valid numeric subject and role', () => {
    expect(parseJwtClaims(jwt({ sub: '42', role: 'ADMIN', exp: 123 }))).toEqual({ sub: 42, role: 'ADMIN', exp: 123 });
  });
  it('rejects malformed, unsupported and non-positive claims', () => {
    expect(parseJwtClaims('bad-token')).toBeNull();
    expect(parseJwtClaims(jwt({ sub: '0', role: 'USER' }))).toBeNull();
    expect(parseJwtClaims(jwt({ sub: '7', role: 'OWNER' }))).toBeNull();
    expect(parseJwtClaims(jwt({ sub: 'not-a-number', role: 'ADMIN' }))).toBeNull();
  });
});
describe('authentication state', () => {
  it('logs in and stores tokens in memory/sessionStorage', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValue({ data: {
      accessToken: jwt({ sub: '12', role: 'USER' }), refreshToken: 'refresh-one', tokenType: 'Bearer', expiresIn: 900,
    } } as never);
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
    await act(async () => {
      await result.current.login({ login: 'user', password: 'password' });
    });
    expect(result.current.status).toBe('authenticated');
    expect(result.current.claims?.sub).toBe(12);
    expect(getRefreshToken()).toBe('refresh-one');
    expect(sessionStorage.getItem('leaderboard.refreshToken')).toBe('refresh-one');
  });
  it('preserves normalized login failure', async () => {
    vi.spyOn(authApi, 'login').mockRejectedValue(new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', undefined, undefined, {
      data: { status: 401, error: 'Unauthorized', message: 'Invalid credentials', path: '/api/auth/login' },
      status: 401, statusText: 'Unauthorized', headers: {}, config: {} as never,
    }));
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
    await expect(result.current.login({ login: 'user', password: 'wrong' })).rejects.toMatchObject({
      status: 401,
      message: 'Invalid credentials',
    });
    expect(result.current.status).toBe('unauthenticated');
  });
  it('registers without starting a session', async () => {
    vi.spyOn(authApi, 'register').mockResolvedValue({ status: 201, data: {
      id: 1, username: 'user', email: 'u@example.com', role: 'USER', registeredAt: 'now', lastLoginAt: null, updatedAt: 'now',
    } } as never);
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
    await act(async () => {
      await result.current.register({ username: 'user', email: 'u@example.com', password: 'password' });
    });
    expect(result.current.status).toBe('unauthenticated');
    expect(getRefreshToken()).toBeNull();
  });
  it('restores exactly once and rotates the refresh token', async () => {
    sessionStorage.setItem('leaderboard.refreshToken', 'old-refresh');
    const refreshSpy = vi.spyOn(authApi, 'refresh').mockResolvedValue({ data: {
      accessToken: jwt({ sub: '15', role: 'ADMIN' }), refreshToken: 'new-refresh', tokenType: 'Bearer', expiresIn: 900,
    } } as never);
    const { result, rerender } = renderHook(() => useAuth(), { wrapper });
    rerender();
    await waitFor(() => expect(result.current.status).toBe('authenticated'));
    expect(refreshSpy).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem('leaderboard.refreshToken')).toBe('new-refresh');
  });
  it('clears startup restoration failure', async () => {
    sessionStorage.setItem('leaderboard.refreshToken', 'bad-refresh');
    vi.spyOn(authApi, 'refresh').mockRejectedValue(new Error('refresh failed'));
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
    expect(sessionStorage.getItem('leaderboard.refreshToken')).toBeNull();
  });
  it('cleans up after logout success and failure', async () => {
    setRefreshToken('refresh');
    vi.spyOn(authApi, 'logout').mockResolvedValue({} as never);
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
    await act(async () => { await result.current.logout(); });
    expect(getRefreshToken()).toBeNull();
    setRefreshToken('refresh');
    vi.mocked(authApi.logout).mockRejectedValue(new Error('logout failed'));
    await act(async () => { await result.current.logout(); });
    expect(getRefreshToken()).toBeNull();
  });
});
describe('refresh concurrency and exclusions', () => {
  it('shares one refresh promise', async () => {
    let resolveRefresh!: (value: never) => void;
    const refreshPromise = new Promise<never>((resolve) => { resolveRefresh = resolve; });
    configureRefreshHandlers({ refresh: () => refreshPromise, onFailure: vi.fn() });
    const first = requestSharedRefresh('old');
    const second = requestSharedRefresh('old');
    expect(first).toBe(second);
    resolveRefresh({} as never);
  });
  it('retries an original 401 request once and does not refresh auth endpoints', async () => {
    setRefreshToken('refresh-old');
    let refreshCalls = 0;
    configureRefreshHandlers({
      refresh: async () => {
        refreshCalls += 1;
        setAccessToken(jwt({ sub: '12', role: 'USER' }).replace('x', 'x'));
        setRefreshToken('refresh-new');
        return {
          accessToken: jwt({ sub: '12', role: 'USER' }),
          refreshToken: 'refresh-new',
          tokenType: 'Bearer',
          expiresIn: 900,
        };
      },
      onFailure: vi.fn(),
    });
    let requestCalls = 0;
    const adapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
      requestCalls += 1;
      if (requestCalls === 1) {
        throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
          data: {}, status: 401, statusText: 'Unauthorized', headers: {}, config,
        });
      }
      throw new axios.AxiosError('Unauthorized again', 'ERR_BAD_REQUEST', config, undefined, {
        data: {}, status: 401, statusText: 'Unauthorized', headers: {}, config,
      });
    };
    await expect(apiClient.get('/api/protected', { adapter })).rejects.toBeDefined();
    expect(requestCalls).toBe(2);
    expect(refreshCalls).toBe(1);
    await expect(apiClient.post('/api/auth/login', { login: 'u', password: 'p' }, { adapter })).rejects.toBeDefined();
    expect(refreshCalls).toBe(1);
  });
  it('excludes auth endpoints and permits normal routes', () => {
    expect(isAuthExclusion('/api/auth/login')).toBe(true);
    expect(isAuthExclusion('/api/auth/register')).toBe(true);
    expect(isAuthExclusion('/api/auth/refresh')).toBe(true);
    expect(isAuthExclusion('/api/auth/logout')).toBe(true);
    expect(isAuthExclusion('/api/posts')).toBe(false);
  });
});
describe('route guards', () => {
  it('renders guest, protected and admin routes according to auth state', async () => {
    vi.spyOn(authApi, 'refresh').mockResolvedValue({ data: {
      accessToken: jwt({ sub: '1', role: 'ADMIN' }), refreshToken: 'rotated', tokenType: 'Bearer', expiresIn: 900,
    } } as never);
    sessionStorage.setItem('leaderboard.refreshToken', 'old-refresh');
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AuthProvider>
          <Routes>
            <Route element={<GuestRoute />}><Route path="/login" element={<div>guest</div>} /></Route>
            <Route element={<ProtectedRoute />}><Route path="/posts" element={<div>protected</div>} /></Route>
            <Route element={<AdminRoute />}><Route path="/admin" element={<div>admin</div>} /></Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );
    await waitFor(() => expect(document.body.textContent).toContain('admin'));
  });
  it('blocks protected content while restoring', () => {
    sessionStorage.setItem('leaderboard.refreshToken', 'pending');
    vi.spyOn(authApi, 'refresh').mockImplementation(() => new Promise(() => undefined) as never);
    render(
      <MemoryRouter initialEntries={['/posts']}>
        <AuthProvider>
          <Routes>
            <Route element={<ProtectedRoute />}><Route path="/posts" element={<div>protected</div>} /></Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );
    expect(document.body.textContent).not.toContain('protected');
  });
  it('restores only once under React StrictMode', async () => {
    sessionStorage.setItem('leaderboard.refreshToken', 'old-refresh');
    const refreshSpy = vi.spyOn(authApi, 'refresh').mockResolvedValue({ data: {
      accessToken: jwt({ sub: '15', role: 'ADMIN' }), refreshToken: 'new-refresh', tokenType: 'Bearer', expiresIn: 900,
    } } as never);

    const strictWrapper = ({ children }: { children: ReactNode }) => (
      <StrictMode><AuthProvider>{children}</AuthProvider></StrictMode>
    );
    const { result } = renderHook(() => useAuth(), { wrapper: strictWrapper });

    await waitFor(() => expect(result.current.status).toBe('authenticated'));
    expect(refreshSpy).toHaveBeenCalledTimes(1);
  });
});