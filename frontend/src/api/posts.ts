import { apiClient } from './client';
import { normalizePage } from './pagination';
import type { ContentRequest, PostDTO, PostEngagementResponse, ProfileActivityResponse, SpringPageParams } from './types';

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

export async function getProfileActivity(userId: number) {
  const response = await apiClient.get<unknown>(`/api/posts/users/${userId}/activity`);
  const data = response.data;
  const keys: (keyof ProfileActivityResponse)[] = [
    'postsCreated', 'commentsAuthored', 'postLikesGiven', 'commentLikesGiven',
    'postLikesReceived', 'commentLikesReceived',
  ];
  if (typeof data !== 'object' || data === null ||
      !keys.every((key) => key in data &&
        typeof (data as Record<string, unknown>)[key] === 'number' &&
        Number.isSafeInteger((data as Record<string, number>)[key]) &&
        (data as Record<string, number>)[key] >= 0)) {
    throw new Error('Invalid activity response.');
  }
  return { ...response, data: data as ProfileActivityResponse };
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
