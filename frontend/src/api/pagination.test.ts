import { describe, expect, it } from 'vitest';
import { normalizePage } from './pagination';
import type { FlatPageResponse, SpringPageDto } from './types';

const nested: SpringPageDto<string> = {
  content: ['a', 'b'],
  page: { number: 0, size: 2, totalElements: 3, totalPages: 2 },
};
const flat: FlatPageResponse<string> = {
  content: ['c'], page: 1, size: 2, totalElements: 3, totalPages: 2, first: false, last: true,
};

describe('normalizePage', () => {
  it('normalizes nested first and flat last pages', () => {
    expect(normalizePage<string>(nested)).toEqual({
      content: ['a', 'b'], number: 0, size: 2, totalElements: 3, totalPages: 2, first: true, last: false,
    });
    expect(normalizePage<string>(flat)).toEqual({
      content: ['c'], number: 1, size: 2, totalElements: 3, totalPages: 2, first: false, last: true,
    });
    expect(normalizePage({ ...nested, content: ['c'], page: { ...nested.page, number: 1 } }))
      .toMatchObject({ number: 1, first: false, last: true });
  });

  it('accepts empty results and Spring out-of-range empty pages for shrink recovery', () => {
    const empty = { content: [], page: { number: 0, size: 5, totalElements: 0, totalPages: 0 } };
    expect(normalizePage(empty)).toEqual({
      content: [], number: 0, size: 5, totalElements: 0, totalPages: 0, first: true, last: true,
    });
    expect(normalizePage({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0, first: true, last: true }))
      .toEqual(normalizePage(empty));
    expect(normalizePage({ ...empty, page: { ...empty.page, number: 2 } }))
      .toMatchObject({ number: 2, first: false, last: true });
    expect(normalizePage({ ...flat, content: [], page: 2, totalElements: 1, totalPages: 1 }))
      .toMatchObject({ number: 2, first: false, last: true });
  });

  it.each([
    null, {}, { ...nested, content: null }, { ...nested, content: {} },
    { content: [] }, { ...nested, page: {} },
    { ...nested, page: { ...nested.page, size: undefined } },
    { ...nested, page: { ...nested.page, number: 0.5 } },
    { ...nested, page: { ...nested.page, number: -1 } },
    { ...nested, page: { ...nested.page, number: NaN } },
    { ...nested, page: { ...nested.page, size: Infinity } },
    { ...nested, page: { ...nested.page, totalElements: Infinity } },
    { ...nested, page: { ...nested.page, totalPages: NaN } },
    { ...nested, page: { ...nested.page, size: 0 } },
    { ...nested, page: { ...nested.page, size: -1 } },
    { ...nested, page: { ...nested.page, totalElements: -1 } },
    { ...nested, page: { ...nested.page, totalPages: -1 } },
    { ...nested, page: { ...nested.page, totalPages: 1 } },
    { ...nested, page: { ...nested.page, number: 2 } },
    { ...nested, content: ['a', 'b', 'c'] },
    { ...flat, first: true },
    { ...flat, last: false },
    { ...flat, last: undefined },
    { ...flat, first: 'false' },
    { ...flat, totalElements: 0, totalPages: 0 },
  ])('rejects malformed or contradictory metadata: %j', (input) => {
    expect(() => normalizePage(input)).toThrow('Invalid pagination response.');
  });
});
