import { apiClient } from './client';
import { updateUserSummary } from './userSummaries';
import type {
  AdminCreateUserRequest,
  AdminSetPasswordRequest,
  UpdateUserRequest,
  UpdateUserRoleRequest,
  UserResponse,
} from './types';

export function createAdminUser(request: AdminCreateUserRequest) {
  return apiClient.post<UserResponse>('/api/admin/users', request);
}

export async function updateAdminUser(id: number, request: UpdateUserRequest) {
  const response = await apiClient.patch<UserResponse>(`/api/admin/users/${id}`, request);
  updateUserSummary(response.data);
  return response;
}

export function deleteAdminUser(id: number) {
  return apiClient.delete<void>(`/api/admin/users/${id}`);
}

export function setAdminUserPassword(id: number, request: AdminSetPasswordRequest) {
  return apiClient.patch<void>(`/api/admin/users/${id}/password`, request);
}

export function updateAdminUserRole(id: number, request: UpdateUserRoleRequest) {
  return apiClient.patch<UserResponse>(`/api/admin/users/${id}/role`, request);
}
