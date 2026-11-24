import { apiClient } from './client';
import { normalizeApiError, type ApiError } from './errors';
import type { UserSummaryResponse } from './types';

interface SummaryState {
  user?: UserSummaryResponse;
  error?: ApiError;
}

const states = new Map<number, SummaryState>();
const listeners = new Map<number, Set<() => void>>();
const requests = new Map<number, Promise<void>>();
const loading: SummaryState = {};

export function getUserSummaryState(id: number): SummaryState {
  return states.get(id) ?? loading;
}

export function subscribeUserSummary(id: number, listener: () => void) {
  const subscribers = listeners.get(id) ?? new Set<() => void>();
  subscribers.add(listener);
  listeners.set(id, subscribers);
  return () => {
    subscribers.delete(listener);
    if (subscribers.size === 0) listeners.delete(id);
  };
}

function publish(id: number, state: SummaryState) {
  states.set(id, state);
  listeners.get(id)?.forEach((listener) => listener());
}

export function updateUserSummary(user: UserSummaryResponse) {
  publish(user.id, { user: { id: user.id, username: user.username } });
}

export function loadUserSummary(id: number): Promise<void> {
  if (states.get(id)?.user) return Promise.resolve();
  const existing = requests.get(id);
  if (existing) return existing;
  const previous = states.get(id);
  const request = apiClient.get<UserSummaryResponse>(`/api/users/${id}/summary`)
    .then((response) => {
      if (response.data.id !== id || typeof response.data.username !== 'string' ||
          !response.data.username.trim()) {
        throw new Error('The user service returned an invalid author summary.');
      }
      // A profile edit may have published a newer name while this request was in flight.
      if (states.get(id) === previous) updateUserSummary(response.data);
    })
    .catch((cause: unknown) => {
      if (states.get(id) === previous) publish(id, { error: normalizeApiError(cause) });
    })
    .finally(() => requests.delete(id));
  requests.set(id, request);
  return request;
}
