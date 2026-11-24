import { apiClient } from './client';
import type { LeaderboardEntryDTO } from './types';

export function getTop10() {
  return apiClient.get<LeaderboardEntryDTO[]>('/api/leaderboard/top-10');
}

export function getTop100() {
  return apiClient.get<LeaderboardEntryDTO[]>('/api/leaderboard/top-100');
}

