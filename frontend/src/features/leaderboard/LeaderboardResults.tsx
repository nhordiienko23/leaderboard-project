import { Medal } from 'lucide-react'
import type { LeaderboardEntryDTO } from '../../api/types'
import { UserName } from '../users/UserName'
import { RankingTransitions } from './RankingTransitions'

interface LeaderboardResultsProps {
  entries: LeaderboardEntryDTO[]
  showPodium: boolean
  currentUserId: number | null
  animateChanges?: boolean
}

const podiumColumn: Record<number, string> = {
  1: 'col-start-2',
  2: 'col-start-1',
  3: 'col-start-3',
}

const podiumAccent: Record<number, string> = {
  1: 'border-amber-400/40 bg-amber-400/5 text-amber-300 sm:-translate-y-2',
  2: 'border-slate-300/25 bg-slate-300/5 text-slate-300',
  3: 'border-orange-400/25 bg-orange-400/5 text-orange-300',
}

const medalAccent: Record<number, string> = {
  1: 'text-amber-300',
  2: 'text-slate-300',
  3: 'text-orange-300',
}

function isCurrentUser(userId: number, currentUserId: number | null) {
  return currentUserId !== null && userId === currentUserId
}

export function LeaderboardResults({ entries, showPodium, currentUserId, animateChanges = false }: LeaderboardResultsProps) {
  const podium = showPodium
    ? [
        entries[0] && { entry: entries[0], rank: 1 },
        entries[1] && { entry: entries[1], rank: 2 },
        entries[2] && { entry: entries[2], rank: 3 },
      ].filter((item): item is { entry: LeaderboardEntryDTO; rank: number } => item !== undefined)
    : []
  const rankedEntries = showPodium ? entries.slice(3) : entries

  return (
    <RankingTransitions entries={entries} showPodium={showPodium} animateChanges={animateChanges}>
      {showPodium && podium.length > 0 && (
        <section aria-label="Top three podium">
          <ol className="grid grid-cols-3 items-end gap-2 sm:gap-4">
            {podium.map(({ entry, rank }) => {
              const isYou = isCurrentUser(entry.userId, currentUserId)
              return (
                <li
                  key={entry.userId}
                  data-ranking-user={entry.userId}
                  className={`min-w-0 rounded-xl border p-2 text-center sm:p-5 ${podiumColumn[rank]} ${podiumAccent[rank]} ${isYou ? 'border-violet-300 bg-violet-500/10 ring-2 ring-violet-300/80' : ''}`}
                >
                  <p className="flex flex-col items-center gap-1 text-[0.65rem] font-semibold uppercase tracking-wide sm:flex-row sm:justify-center sm:gap-1.5 sm:text-sm">
                    <Medal
                      aria-label={`${rank === 1 ? 'First' : rank === 2 ? 'Second' : 'Third'} place medal`}
                      role="img"
                      className={`h-4 w-4 shrink-0 ${medalAccent[rank]}`}
                    />
                    <span>{rank === 1 ? '1st place' : rank === 2 ? '2nd place' : '3rd place'}</span>
                  </p>
                  <div className="mt-2 min-w-0 break-words text-sm font-semibold text-lb-text sm:text-base">
                    <UserName userId={entry.userId} profileLink />
                  </div>
                  {isYou && <span className="mt-2 inline-flex rounded-full bg-violet-400/20 px-2 py-0.5 text-xs font-semibold text-violet-100">You</span>}
                  <p data-ranking-points className="mt-3 whitespace-nowrap text-xs font-bold tabular-nums text-lb-text sm:text-base">
                    {entry.points} <span className="text-xs font-medium text-lb-muted">pts</span>
                  </p>
                </li>
              )
            })}
          </ol>
        </section>
      )}

      {rankedEntries.length > 0 && (
        <ol aria-label="Leaderboard rankings" className="rounded-xl border border-lb-border bg-lb-surface divide-y divide-lb-border">
          {rankedEntries.map((entry, index) => {
            const rank = (showPodium ? 3 : 0) + index + 1
            const isYou = isCurrentUser(entry.userId, currentUserId)
            return (
              <li
                key={entry.userId}
                data-ranking-user={entry.userId}
                className={`flex min-w-0 items-center gap-3 px-4 py-3 first:rounded-t-xl last:rounded-b-xl transition-colors hover:bg-lb-elevated/60 sm:px-5 ${isYou ? 'border-l-2 border-violet-300 bg-violet-500/10' : ''}`}
              >
                <span className="w-10 shrink-0 text-sm font-semibold text-lb-muted">#{rank}</span>
                <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1 break-words">
                  <span className="min-w-0 max-w-full">
                    <UserName userId={entry.userId} profileLink />
                  </span>
                  {isYou && <span className="rounded-full bg-violet-400/20 px-2 py-0.5 text-xs font-semibold text-violet-100">You</span>}
                </div>
                <span data-ranking-points className="shrink-0 text-right text-sm font-semibold tabular-nums text-lb-text">
                  {entry.points} <span className="text-xs font-medium text-lb-muted">pts</span>
                </span>
              </li>
            )
          })}
        </ol>
      )}
    </RankingTransitions>
  )
}
