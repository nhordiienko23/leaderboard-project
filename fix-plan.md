# Users date and live leaderboard plan

## Problem and approach

The users directory formats Joined dates using the browser locale, while the profile already specifies English. Top 100 currently omits the existing podium. The leaderboard only fetches when opened, switched, or retried. Use the existing HTTP endpoints to refresh the selected leaderboard every 5 seconds; keep the current content visible during background refreshes, with no ranking animation.

## Todos

1. **Fix the users directory Joined date.** Update `frontend/src/features/users/UserTable.tsx` to use the same `en-US` abbreviated month, day, and year format as the profile. Preserve the existing local-time interpretation and `<time datetime>` value. Add a directory test with a non-English default locale and an offset-date case.
2. **Reuse the podium for Top 100.** Update `frontend/src/pages/leaderboard/LeaderboardPage.tsx` and, if useful, simplify `frontend/src/features/leaderboard/LeaderboardResults.tsx` so both ranges present the first three entries on the existing podium and show ranks 4 onward below, with no duplicates. Preserve backend order, profile links, current-user highlighting, responsive layout, and empty or fewer-than-three behavior. Update Top 100 expectations in the leaderboard tests, including 100 entries and short lists.
3. **Refresh the selected range smoothly.** Extend the leaderboard page's loading lifecycle to poll its existing endpoint every 5 seconds while mounted. Distinguish initial/range-switch/retry loads from background refreshes, keeping successful data and empty states visible without a spinner during refresh. Preserve request sequencing so stale results cannot overwrite a newer selection; avoid overlapping refreshes, clean up timers and pending responses on range change/unmount, and refresh promptly when returning to a visible tab if polling is paused while hidden. Keep the last successful data on refresh failure, show an unobtrusive accessible warning, retry on the next interval, and clear the warning after success. Preserve current initial-load error and manual retry behavior.
4. **Validate behavior.** Add deterministic timer and deferred-request tests for both ranges, data and rank changes, transitions between empty and populated results, no flicker, failure and recovery, range switches, unmount cleanup, and non-overlapping/stale requests. Run the relevant users and leaderboard tests, then the frontend build/type-check; update any affected integration expectations.

## Considerations

- Five-second polling provides near-real-time updates after the backend has processed changes, not instantaneous push delivery; no backend streaming or new dependency is required.
- Preserve the existing range-switch loading behavior and do not change unrelated admin date formatting.
- Existing repository changes are user-owned and must be preserved.
