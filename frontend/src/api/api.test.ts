import { describe, expect, expectTypeOf, it, vi, beforeEach } from 'vitest';
import axios, { type AxiosAdapter } from 'axios';

import { apiClient, apiBaseUrl, getAccessToken, setAccessToken } from './client';
import * as auth from './auth';
import * as posts from './posts';
import * as comments from './comments';
import * as likes from './likes';
import * as users from './users';
import * as admin from './admin';
import * as leaderboard from './leaderboard';
import { normalizeApiError } from './errors';
import type { CommentEngagementResponse, PostEngagementResponse } from './types';

beforeEach(() => {
  vi.restoreAllMocks();
  vi.spyOn(apiClient, 'get').mockResolvedValue({
    data: { content: [], page: { number: 0, size: 5, totalElements: 0, totalPages: 0 } },
  } as never);
  vi.spyOn(apiClient, 'post').mockResolvedValue({} as never);
  vi.spyOn(apiClient, 'put').mockResolvedValue({} as never);
  vi.spyOn(apiClient, 'patch').mockResolvedValue({ data: { id: 9, username: 'new' } } as never);
  vi.spyOn(apiClient, 'delete').mockResolvedValue({} as never);
});

describe('centralized client', () => {
  it('uses the gateway fallback base URL', () => {
    expect(apiBaseUrl).toBe('http://localhost:8081');
  });

  it('adds and removes the bearer authorization header in memory', async () => {
    vi.restoreAllMocks();

    setAccessToken('test-access-token');
    expect(getAccessToken()).toBe('test-access-token');

    let authorization: unknown;
    const adapter: AxiosAdapter = async (config) => {
      authorization = config.headers?.Authorization;
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
    };

    await apiClient.get('/api/test', { adapter });
    expect(authorization).toBe('Bearer test-access-token');

    setAccessToken(null);
    await apiClient.get('/api/test', { adapter });
    expect(getAccessToken()).toBeNull();
  });
});

describe('endpoint methods, URLs, bodies and params', () => {
  it('implements authentication endpoints', () => {
    auth.register({ username: 'user', email: 'u@example.com', password: 'pw' });
    expect(apiClient.post).toHaveBeenCalledWith('/api/auth/register', { username: 'user', email: 'u@example.com', password: 'pw' });
    auth.login({ login: 'user', password: 'pw' });
    expect(apiClient.post).toHaveBeenCalledWith('/api/auth/login', { login: 'user', password: 'pw' });
    auth.refresh({ refreshToken: 'refresh' });
    expect(apiClient.post).toHaveBeenCalledWith('/api/auth/refresh', { refreshToken: 'refresh' });
    auth.logout({ refreshToken: 'refresh' });
    expect(apiClient.post).toHaveBeenCalledWith('/api/auth/logout', { refreshToken: 'refresh' });
  });

  it('implements post endpoints', async () => {
    await posts.getPosts({ page: 2, size: 10, sort: 'id,desc' });
    expect(apiClient.get).toHaveBeenCalledWith('/api/posts', { params: { page: 2, size: 10, sort: 'id,desc' } });
    posts.getPost(7); expect(apiClient.get).toHaveBeenCalledWith('/api/posts/7');
    const postEngagement = posts.getPostEngagement(7);
    expect(apiClient.get).toHaveBeenCalledWith('/api/posts/7/engagement');
    expectTypeOf(postEngagement).resolves.toMatchTypeOf<{ data: PostEngagementResponse }>();
    posts.createPost({ content: 'hello' }); expect(apiClient.post).toHaveBeenCalledWith('/api/posts', { content: 'hello' });
    posts.updatePost(7, { content: 'edited' }); expect(apiClient.put).toHaveBeenCalledWith('/api/posts/7', { content: 'edited' });
    posts.deletePost(7); expect(apiClient.delete).toHaveBeenCalledWith('/api/posts/7');
  });

  it('implements comment endpoints', async () => {
    await comments.getComments(3, { page: 1, size: 5 });
    expect(apiClient.get).toHaveBeenCalledWith('/api/posts/3/comments', { params: { page: 1, size: 5 } });
    comments.getComment(3, 4); expect(apiClient.get).toHaveBeenCalledWith('/api/posts/3/comments/4');
    const commentEngagement = comments.getCommentEngagement(3, 4);
    expect(apiClient.get).toHaveBeenCalledWith('/api/posts/3/comments/4/engagement');
    expectTypeOf(commentEngagement).resolves.toMatchTypeOf<{ data: CommentEngagementResponse }>();
    comments.createComment(3, { content: 'comment' }); expect(apiClient.post).toHaveBeenCalledWith('/api/posts/3/comments', { content: 'comment' });
    comments.updateComment(3, 4, { content: 'edited' }); expect(apiClient.put).toHaveBeenCalledWith('/api/posts/3/comments/4', { content: 'edited' });
    comments.deleteComment(3, 4); expect(apiClient.delete).toHaveBeenCalledWith('/api/posts/3/comments/4');
  });

  it('implements like endpoints', () => {
    likes.likePost(1); expect(apiClient.post).toHaveBeenCalledWith('/api/posts/1/likes');
    likes.unlikePost(1); expect(apiClient.delete).toHaveBeenCalledWith('/api/posts/1/likes');
    likes.likeComment(1, 2); expect(apiClient.post).toHaveBeenCalledWith('/api/posts/1/comments/2/likes');
    likes.unlikeComment(1, 2); expect(apiClient.delete).toHaveBeenCalledWith('/api/posts/1/comments/2/likes');
  });

  it('implements user endpoints', async () => {
    users.getUser(9); expect(apiClient.get).toHaveBeenCalledWith('/api/users/9');
    await users.getUsers({ page: 0, size: 20, sort: 'username,asc' });
    expect(apiClient.get).toHaveBeenCalledWith('/api/users', { params: { page: 0, size: 20, sort: 'username,asc' } });
    await users.updateMyProfile({ username: 'new' }); expect(apiClient.patch).toHaveBeenCalledWith('/api/users/me', { username: 'new' });
    users.deleteMyAccount(); expect(apiClient.delete).toHaveBeenCalledWith('/api/users/me');
    users.changeMyPassword({ currentPassword: 'old', newPassword: 'new' });
    expect(apiClient.patch).toHaveBeenCalledWith('/api/users/me/password', { currentPassword: 'old', newPassword: 'new' });
  });

  it('implements admin endpoints', async () => {
    admin.createAdminUser({ username: 'u', email: 'u@e.com', password: 'pw', role: 'USER' });
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/users', { username: 'u', email: 'u@e.com', password: 'pw', role: 'USER' });
    await admin.updateAdminUser(5, { email: 'a@e.com' }); expect(apiClient.patch).toHaveBeenCalledWith('/api/admin/users/5', { email: 'a@e.com' });
    admin.deleteAdminUser(5); expect(apiClient.delete).toHaveBeenCalledWith('/api/admin/users/5');
    admin.setAdminUserPassword(5, { newPassword: 'pw' }); expect(apiClient.patch).toHaveBeenCalledWith('/api/admin/users/5/password', { newPassword: 'pw' });
    admin.updateAdminUserRole(5, { role: 'ADMIN' }); expect(apiClient.patch).toHaveBeenCalledWith('/api/admin/users/5/role', { role: 'ADMIN' });
  });

  it('implements leaderboard endpoints', () => {
    leaderboard.getTop10(); expect(apiClient.get).toHaveBeenCalledWith('/api/leaderboard/top-10');
    leaderboard.getTop100(); expect(apiClient.get).toHaveBeenCalledWith('/api/leaderboard/top-100');
  });
});

describe('error normalization', () => {
  it('preserves post field errors and backend message', () => {
    const error = new axios.AxiosError('validation', 'ERR_BAD_REQUEST', undefined, undefined, {
      data: { timestamp: '2026-01-01', status: 400, error: 'Bad Request', message: 'Request validation failed.', path: '/api/posts', fieldErrors: { content: 'content cannot be empty' } },
      status: 400, statusText: 'Bad Request', headers: {}, config: {} as never,
    });
    expect(normalizeApiError(error)).toMatchObject({ status: 400, message: 'Request validation failed.', fieldErrors: { content: 'content cannot be empty' }, isNetworkError: false });
  });

  it('handles user error bodies without field errors', () => {
    const error = new axios.AxiosError('missing', 'ERR_NOT_FOUND', undefined, undefined, {
      data: { status: 404, error: 'Not Found', message: 'User not found', path: '/api/users/1' },
      status: 404, statusText: 'Not Found', headers: {}, config: {} as never,
    });
    expect(normalizeApiError(error)).toMatchObject({ status: 404, message: 'User not found', fieldErrors: {} });
  });

  it('handles network failures and empty bodies', () => {
    const network = new axios.AxiosError('Network Error', 'ERR_NETWORK');
    expect(normalizeApiError(network)).toEqual(expect.objectContaining({ status: null, isNetworkError: true, message: expect.stringContaining('Network error') }));

    const empty = new axios.AxiosError('failed', 'ERR_BAD_REQUEST', undefined, undefined, {
      data: undefined, status: 500, statusText: 'Server Error', headers: {}, config: {} as never,
    });
    expect(normalizeApiError(empty)).toMatchObject({ status: 500, fieldErrors: {}, isNetworkError: false });
  });
});
