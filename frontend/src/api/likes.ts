import { apiClient } from './client';
import type { LikeDTO } from './types';

export function likePost(postId: number) {
  return apiClient.post<LikeDTO>(`/api/posts/${postId}/likes`);
}

export function unlikePost(postId: number) {
  return apiClient.delete<void>(`/api/posts/${postId}/likes`);
}

export function likeComment(postId: number, commentId: number) {
  return apiClient.post<LikeDTO>(`/api/posts/${postId}/comments/${commentId}/likes`);
}

export function unlikeComment(postId: number, commentId: number) {
  return apiClient.delete<void>(`/api/posts/${postId}/comments/${commentId}/likes`);
}

