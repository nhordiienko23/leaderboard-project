import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from './client';
import { getComments } from './comments';
import { getPosts } from './posts';
import { getUsers } from './users';

const nested = { content: [{ id: 1 }], page: { number: 0, size: 5, totalElements: 6, totalPages: 2 } };
const flat = { content: [{ id: 2 }], page: 1, size: 20, totalElements: 21, totalPages: 2, first: false, last: true };

beforeEach(() => vi.restoreAllMocks());

describe('paginated API methods', () => {
  it('normalizes posts and comments while retaining Axios response and request parameters', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: nested, status: 200 } as never);
    const posts = await getPosts({ page: 0, size: 5, sort: 'id,desc' });
    const comments = await getComments(7, { page: 0, size: 5, sort: 'id,asc' });
    expect(posts).toMatchObject({ status: 200, data: { content: nested.content, number: 0, size: 5, totalElements: 6, totalPages: 2, first: true, last: false } });
    expect(comments.data).toEqual(posts.data);
    expect(get).toHaveBeenNthCalledWith(1, '/api/posts', { params: { page: 0, size: 5, sort: 'id,desc' } });
    expect(get).toHaveBeenNthCalledWith(2, '/api/posts/7/comments', { params: { page: 0, size: 5, sort: 'id,asc' } });
  });

  it('normalizes the flat user directory response', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: flat } as never);
    expect((await getUsers({ page: 1, size: 20 })).data).toEqual({
      content: flat.content, number: 1, size: 20, totalElements: 21, totalPages: 2, first: false, last: true,
    });
    expect(get).toHaveBeenCalledWith('/api/users', { params: { page: 1, size: 20 } });
  });

  it.each([
    ['posts', () => getPosts()],
    ['comments', () => getComments(7)],
    ['users', () => getUsers()],
  ])('rejects malformed %s responses', async (_label, request) => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { content: [], page: {} } } as never);
    await expect(request()).rejects.toThrow('Invalid pagination response.');
  });
});
