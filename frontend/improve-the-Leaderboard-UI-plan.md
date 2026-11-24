## Task: Improve the Leaderboard UI

Redesign the existing Leaderboard page to make it look like a polished, modern developer-community platform.

### Important constraints

- Work only in the frontend.
- Do not modify backend services, API contracts, database schemas, or leaderboard scoring logic.
- Preserve the existing Top 10 / Top 100 functionality.
- Reuse the existing API client, endpoint modules, components, types, and authentication context.
- Do not introduce new dependencies.
- Follow the existing project conventions and Tailwind CSS styling.
- Keep the implementation responsive and accessible.
- Avoid unnecessary abstractions and overengineering.

### 1. Leaderboard header

Improve the existing header:
- Keep the title "Leaderboard".
- Keep the description "See how the community ranks by points."
- Add a subtle trophy icon using the existing Lucide React dependency.
- Maintain the existing visual style of the application.

### 2. Top 10 / Top 100 selector

Keep the existing Top 10 and Top 100 controls.

Improve their appearance:
- Use a modern segmented-control or tab-like design.
- Clearly highlight the currently selected option.
- Preserve the existing API behavior and data loading logic.
- Switching between Top 10 and Top 100 must continue to work correctly.

### 3. Top 3 podium

When displaying Top 10:
- Display the first three users in a podium layout above the remaining rankings.
- First place should be visually emphasized and positioned in the center.
- Second place should appear on the left.
- Third place should appear on the right.
- Use gold, silver, and bronze visual accents.
- Display each user's username and points.
- Use medal icons or subtle decorative elements.
- Do not use excessive gradients, animations, or oversized cards.

Handle fewer than three users gracefully. Never invent leaderboard entries.

### 4. Ranking list

For Top 10:
- Display positions 4–10 below the podium.
- Use clean, compact rows.
- Display rank, username, and points.
- Keep points right-aligned.
- Use subtle separators and hover states.

For Top 100:
- Display all returned users in a compact, readable list.
- Do not display the podium.
- Preserve the ranking order returned by the backend.
- Make the list easy to scan.
- Ensure the layout works on mobile screens.

### 5. Current user's position

If the existing API response or available frontend data provides the current user's ranking:
- Highlight the current user's row.
- Use a subtle background or border to distinguish it.
- Display a small "You" label.
- If the current user is outside the displayed Top 10 or Top 100, preserve any existing separate current-user ranking information.
- Do not make additional API assumptions or invent ranking data.

### 6. Usernames

Display actual usernames instead of generated labels such as "User #1" or "user-1", wherever the existing frontend data makes usernames available.

Do not modify unrelated author-name handling in posts or comments as part of this task.

If the leaderboard API currently returns fallback usernames, do not hide the underlying issue with frontend-generated names. Keep the existing fallback behavior until the backend is fixed.

### 7. Loading, empty, and error states

Preserve and improve existing states:
- Loading state: show a lightweight skeleton or loading indicator.
- Empty state: display a friendly message when no users are available.
- Error state: preserve the existing error message and Retry functionality.
- Switching between Top 10 and Top 100 must not cause stale responses to overwrite newer results.

### 8. Responsive design

Desktop:
- Centered podium with three balanced cards.
- Ranking list below.

Mobile:
- Compact podium cards.
- No horizontal page overflow.
- Ranking rows remain readable.
- Username and points must not overlap.

### 9. Testing

Add or update tests using the existing Vitest and React Testing Library setup.

Cover:
- Top 10 displays the podium and remaining users.
- Top 100 displays a compact list without the podium.
- Switching between Top 10 and Top 100 works.
- Fewer than three users are handled correctly.
- The current user is highlighted when ranking information is available.
- Loading, empty, and error states still work.
- Retry functionality still works.
- Responsive behavior where practical through existing testing conventions.

### 10. Final verification

Run:
- The complete frontend test suite.
- TypeScript checks.
- ESLint.
- Production build.

Fix any regressions introduced by your changes.

At the end, provide a concise summary of:
1. Files changed.
2. UI improvements implemented.
3. Tests added or updated.
4. Results of all verification commands.

Do not change backend code or API contracts.