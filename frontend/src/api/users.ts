import { apiClient } from './client';
import { normalizePage } from './pagination';
import { updateUserSummary } from './userSummaries';
import type {
  ChangePasswordRequest,
  SpringPageParams,
  UpdateUserRequest,
  UserResponse,
} from './types';

export function getUser(id: number) {
  return apiClient.get<UserResponse>(`/api/users/${id}`);
}

export async function getUsers(params: SpringPageParams = {}) {
  const response = await apiClient.get<unknown>('/api/users', { params });
  return { ...response, data: normalizePage<UserResponse>(response.data) };
}

export async function updateMyProfile(request: UpdateUserRequest) {
  const response = await apiClient.patch<UserResponse>('/api/users/me', request);
  updateUserSummary(response.data);
  return response;
}

export function deleteMyAccount() {
  return apiClient.delete<void>('/api/users/me');
}

export function changeMyPassword(request: ChangePasswordRequest) {
  return apiClient.patch<void>('/api/users/me/password', request);
}
