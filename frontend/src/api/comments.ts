import { apiClient } from './client';
import { normalizePage } from './pagination';
import type { CommentDTO, CommentEngagementResponse, ContentRequest, SpringPageParams } from './types';

export async function getComments(postId: number, params: SpringPageParams = {}) {
  const response = await apiClient.get<unknown>(`/api/posts/${postId}/comments`, { params });
  return { ...response, data: normalizePage<CommentDTO>(response.data) };
}

export function getComment(postId: number, commentId: number) {
  return apiClient.get<CommentDTO>(`/api/posts/${postId}/comments/${commentId}`);
}

export function getCommentEngagement(postId: number, commentId: number) {
  return apiClient.get<CommentEngagementResponse>(`/api/posts/${postId}/comments/${commentId}/engagement`);
}

export function createComment(postId: number, request: ContentRequest) {
  return apiClient.post<CommentDTO>(`/api/posts/${postId}/comments`, request);
}

export function updateComment(postId: number, commentId: number, request: ContentRequest) {
  return apiClient.put<CommentDTO>(`/api/posts/${postId}/comments/${commentId}`, request);
}

export function deleteComment(postId: number, commentId: number) {
  return apiClient.delete<void>(`/api/posts/${postId}/comments/${commentId}`);
}
