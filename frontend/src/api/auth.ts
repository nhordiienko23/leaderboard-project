import { apiClient } from './client';
import type {
  LoginRequest,
  RegisterRequest,
  RefreshTokenRequest,
  LogoutRequest,
  TokenResponse,
  UserResponse,
} from './types';

export function register(request: RegisterRequest) {
  return apiClient.post<UserResponse>('/api/auth/register', request);
}

export function login(request: LoginRequest) {
  return apiClient.post<TokenResponse>('/api/auth/login', request);
}

export function refresh(request: RefreshTokenRequest) {
  return apiClient.post<TokenResponse>('/api/auth/refresh', request);
}

export function logout(request: LogoutRequest) {
  return apiClient.post<void>('/api/auth/logout', request);
}

