import { apiClient } from './client';
import { normalizePage } from './pagination';
import type { ContentRequest, PostDTO, PostEngagementResponse, SpringPageParams } from './types';

export async function getPosts(params: SpringPageParams = {}) {
  const response = await apiClient.get<unknown>('/api/posts', { params });
  return { ...response, data: normalizePage<PostDTO>(response.data) };
}

export function getPost(id: number) {
  return apiClient.get<PostDTO>(`/api/posts/${id}`);
}

export function getPostEngagement(id: number) {
  return apiClient.get<PostEngagementResponse>(`/api/posts/${id}/engagement`);
}

export function createPost(request: ContentRequest) {
  return apiClient.post<PostDTO>('/api/posts', request);
}

export function updatePost(id: number, request: ContentRequest) {
  return apiClient.put<PostDTO>(`/api/posts/${id}`, request);
}

export function deletePost(id: number) {
  return apiClient.delete<void>(`/api/posts/${id}`);
}
