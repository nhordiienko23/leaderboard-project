import { createContext, useContext, useEffect, useMemo, useReducer, useRef, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import { setAccessToken } from '../api/client';
import type { LoginRequest, RegisterRequest, TokenResponse, UserResponse } from '../api/types';
import { normalizeApiError } from '../api/errors';
import { clearRefreshToken, getRefreshToken, restoreRefreshToken, setRefreshToken } from './authState';
import { parseJwtClaims, type JwtClaims } from './jwt';
import { configureRefreshHandlers, resetSharedRefresh } from './refreshManager';

export type AuthStatus = 'restoring' | 'authenticated' | 'unauthenticated';

interface AuthState {
  status: AuthStatus;
  claims: JwtClaims | null;
}

interface AuthContextValue extends AuthState {
  login: (request: LoginRequest) => Promise<TokenResponse>;
  register: (request: RegisterRequest) => Promise<UserResponse>;
  logout: () => Promise<void>;
  clearAuth: () => void;
  isAuthenticated: boolean;
  isAdmin: boolean;
}

const initialState: AuthState = { status: 'restoring', claims: null };

type Action =
  | { type: 'authenticated'; claims: JwtClaims }
  | { type: 'unauthenticated' };

function authReducer(_state: AuthState, action: Action): AuthState {
  if (action.type === 'authenticated') {
    return { status: 'authenticated', claims: action.claims };
  }
  return { status: 'unauthenticated', claims: null };
}

const AuthContext = createContext<AuthContextValue | null>(null);

function validateTokenResponse(response: TokenResponse): JwtClaims {
  if (!response || typeof response.accessToken !== 'string' || !response.accessToken.trim() ||
      typeof response.refreshToken !== 'string' || !response.refreshToken.trim()) {
    throw new Error('The authentication service returned an invalid token response.');
  }
  const claims = parseJwtClaims(response.accessToken);
  if (!claims) throw new Error('The authentication token is invalid.');
  return claims;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(authReducer, initialState);
  const startupRestoreStarted = useRef(false);

  const clearAuth = () => {
    setAccessToken(null);
    clearRefreshToken();
    resetSharedRefresh();
    dispatch({ type: 'unauthenticated' });
  };

  const applyTokenResponse = (response: TokenResponse): JwtClaims => {
    const claims = validateTokenResponse(response);
    setAccessToken(response.accessToken);
    setRefreshToken(response.refreshToken);
    dispatch({ type: 'authenticated', claims });
    return claims;
  };

  useEffect(() => {
    if (startupRestoreStarted.current) return;
    startupRestoreStarted.current = true;

    const restore = async () => {
      const refreshToken = restoreRefreshToken();
      if (!refreshToken) {
        setAccessToken(null);
        dispatch({ type: 'unauthenticated' });
        return;
      }

      try {
        const response = await authApi.refresh({ refreshToken });
        applyTokenResponse(response.data);
      } catch {
        clearAuth();
      }
    };

    void restore();
  }, []);

  useEffect(() => {
    configureRefreshHandlers({
      refresh: async (refreshToken: string) => {
        const response = await authApi.refresh({ refreshToken });
        applyTokenResponse(response.data);
        return response.data;
      },
      onFailure: clearAuth,
    });

    return () => {
      resetSharedRefresh();
    };
  }, []);

  const login = async (request: LoginRequest): Promise<TokenResponse> => {
    try {
      const response = await authApi.login(request);
      applyTokenResponse(response.data);
      return response.data;
    } catch (error) {
      throw normalizeApiError(error);
    }
  };

  const register = async (request: RegisterRequest): Promise<UserResponse> => {
    try {
      const response = await authApi.register(request);
      if (response.status !== 201) {
        throw new Error('The authentication service did not confirm registration.');
      }
      return response.data;
    } catch (error) {
      throw normalizeApiError(error);
    }
  };

  const logout = async (): Promise<void> => {
    const refreshToken = getRefreshToken() ?? restoreRefreshToken();
    try {
      if (refreshToken) {
        await authApi.logout({ refreshToken });
      }
    } catch {
      // Cleanup is deliberately unconditional.
    } finally {
      clearAuth();
    }
  };

  const value = useMemo<AuthContextValue>(() => ({
    ...state,
    login,
    register,
    logout,
    clearAuth,
    isAuthenticated: state.status === 'authenticated',
    isAdmin: state.status === 'authenticated' && state.claims?.role === 'ADMIN',
  }), [state]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider.');
  return context;
}
