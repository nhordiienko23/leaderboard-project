import type { PageResponse } from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonnegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}

export function normalizePage<T>(value: unknown): PageResponse<T> {
  const invalid = () => new Error('Invalid pagination response.');
  if (!isRecord(value) || !Array.isArray(value.content)) throw invalid();

  const metadata = isRecord(value.page) ? value.page : value;
  const nested = metadata !== value;
  const number = nested ? metadata.number : metadata.page;
  const { size, totalElements, totalPages } = metadata;
  if (!isNonnegativeInteger(number) || !isNonnegativeInteger(size) || size === 0 ||
      !isNonnegativeInteger(totalElements) || !isNonnegativeInteger(totalPages)) {
    throw invalid();
  }

  const first = number === 0;
  const last = number >= totalPages - 1;
  if (totalPages !== Math.ceil(totalElements / size) ||
      value.content.length > size || value.content.length > totalElements ||
      (number >= totalPages && value.content.length !== 0) ||
      (!nested && (value.first !== first || value.last !== last))) {
    throw invalid();
  }

  return { content: value.content as T[], number, size, totalElements, totalPages, first, last };
}
