# Frontend

Account identity, post/comment authors, and leaderboard names are resolved from the user
database through `GET /api/users/{id}/summary`. This public gateway endpoint returns only
`id` and `username`; full profiles still require authentication. Concurrent lookups for the
same author are shared, and profile edits update mounted name labels immediately.
Missing users display "Deleted user"; lookup failures are explicit, with a retry action on author
labels. Account-menu failures show "Username unavailable" without nesting a button inside the menu button.

Leaderboard ordering, points, and ranks come from leaderboard-service. Each displayed username
is resolved separately through the authoritative user-summary endpoint rather than using the
leaderboard projection's username. Names show "Loading username..." while the summary request is
pending; a confirmed 404 shows "Deleted user", and other failures show "Username unavailable"
with a retry action instead of falling back to the projection name. Successful summaries are
cached and concurrent requests for the same user are shared.

## Setup and local development

From `frontend/`, install the locked dependencies and run the development server:

```powershell
npm.cmd ci
$env:VITE_API_BASE_URL = 'http://localhost:8081'
npm.cmd run dev
```

All browser API requests use the centralized Axios client and API Gateway. Configure
`VITE_API_BASE_URL` for the gateway origin; never point the frontend at a service port. The
gateway must allow the exact Vite browser origin through `GATEWAY_CORS_ALLOWED_ORIGINS`.

## Frontend behavior

- Posts and comments use Spring pagination with five items per page. Creating a post reloads
  page zero; comment creation and deletion reload the current page and fall back when its last
  item is deleted. Comment drafts remain mounted while pages refresh.
- The pagination adapter accepts nested Spring Data responses (`content` and a `page` object
  containing `number`, `size`, `totalElements`, and `totalPages`) and the existing flat user
  response (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, and `last`).
  It validates the metadata and normalizes both formats to `content`, zero-based `number`,
  `size`, `totalElements`, `totalPages`, `first`, and `last`; malformed responses enter the
  existing load-error state.
- Internal page indexes and API requests are zero-based. Pagination controls display one-based
  page numbers, and the shared component converts selections and direct page-number input back
  to the existing zero-based page callback.
- Shared pagination provides First, Previous, bounded numbered pages (with ellipses when needed),
  Next, Last, and validated direct page navigation. It owns presentation and input validation,
  not data fetching; each list retains its request lifecycle, stale-response protection, retries,
  and page-shrink recovery.
- The gateway exposes read-only `GET /api/posts/{postId}/engagement` and
  `GET /api/posts/{postId}/comments/{commentId}/engagement` summaries. Post summaries return
  `{ likeCount, commentCount, likedByMe }`; comment summaries return
  `{ likeCount, likedByMe }`. Counts are available anonymously, with `likedByMe: null`;
  authenticated responses report the viewer's `true`/`false` state.
- Authenticated Like and Unlike actions reflect confirmed server state; Unlike removes an
  existing like and is not a separate reaction. Anonymous visitors see counts without mutation
  controls. Summary failures keep the resource visible and disable mutations until retry.
- Success notices and benign duplicate/missing-like warnings fade during the final 400 ms and
  disappear after 4 seconds. The fade is disabled for reduced-motion preferences. Genuine
  network, authorization, missing-resource, and server errors remain visible.
- Registration, account settings, and admin email fields provide the accessible guidance
  `Enter a valid email address, for example name@example.com.` Required, overlength, and
  server-side errors remain distinct from that guidance.
- Public-profile dates use English abbreviated months (for example `Nov 23, 2026`) while
  preserving the source date's existing timezone interpretation. Profiles without a login show
  `No login recorded`.

## Local checks

From `frontend/`, run the complete verification set:

```powershell
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

`npm.cmd run lint` runs the TypeScript project check (`tsc -b --pretty false`); there is no separate
type-check script. The build also type-checks before producing the Vite bundle.

The integration tests render the application against an Axios adapter that simulates gateway
responses. They verify that browser-facing feature flows use the centralized client and `/api/`
gateway routes; they do not replace manual verification against the running backend.

## Gateway-backed manual test matrix

### Start the gateway with an explicit browser origin

From the repository root, configure the exact origin used by Vite before starting the stack:

```powershell
$env:GATEWAY_CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
docker compose up --build -d
```

The gateway allows only configured origins. If Vite uses another host or port, set that exact
origin instead. Do not use a wildcard. In `frontend/`, set the gateway base URL and start Vite:

```powershell
$env:VITE_API_BASE_URL = 'http://localhost:8081'
npm.cmd run dev
```

Use authorized test accounts and disposable data. Verify browser Network requests target the
gateway on port `8081`, never service ports. Confirm the preflight `OPTIONS` response allows the
configured origin and required headers before investigating application-level failures.

| Area | Manual action | Expected result |
| --- | --- | --- |
| Public feed | Open `/posts`, paginate, and open a post | `GET /api/posts` and `GET /api/posts/{id}` succeed through the gateway; Spring page fields render correctly. |
| Comments | Open a post and page its comments | `GET /api/posts/{postId}/comments` succeeds; empty and unavailable states are understandable. |
| Authentication | Register and log in with disposable credentials, reload, then log out | Register returns `201`; login and refresh return `200`; refresh rotation is retained for the session; logout returns `204` and clears auth state. |
| Post/comment mutations | As the owner, create, edit, and delete a post and comment | Requests use the documented `/api/posts` routes; content changes only after success and deletion requires confirmation. |
| Engagement | View posts and comments anonymously, then log in and like/unlike them | `GET /api/posts/{postId}/engagement` and `GET /api/posts/{postId}/comments/{commentId}/engagement` provide confirmed totals and the viewer state. Anonymous totals are read-only; authenticated Like/Unlike controls refresh the summary after a mutation. |
| Leaderboard | Open the leaderboard and switch Top 10 / Top 100 | `GET /api/leaderboard/top-10` and `/top-100` succeed; displayed rank follows array position. |
| User directory/profile | With an authenticated account, page `/users` and open a user profile | `GET /api/users` uses supported pagination; `GET /api/users/{id}` displays the returned profile. |
| Account settings | Update profile, change password, and delete a disposable account | Only the documented `/api/users/me` mutation routes are sent; successful account deletion clears auth state. |
| Admin | As an admin, create/edit/change role/reset password/delete a test user; repeat as a regular user | Admin routes work for admins; admin controls/routes are unavailable to non-admin users; gateway authorization remains authoritative. |
| Error handling | Exercise invalid form values, a missing resource, a forbidden action, and a stopped gateway | Validation, 404, 403, and retryable network feedback are shown without exposing tokens. |
| Responsive/accessibility | Repeat primary navigation and dialog flows at mobile, tablet, and desktop widths using keyboard only | Navigation/dialog focus works, focus remains visible, labels/headings are semantic, and icon-only controls are named. |

Use `/register` to create a disposable account, then `/login` to start a session. Registration
does not automatically sign the new user in.
- Leaderboard rank changes use 300 ms FLIP movement across the podium and ranking list,
  with a subtle entry fade and points-opacity transition. Only consecutive successful background
  refreshes of the same range animate; unchanged responses, initial loads, retries, errors,
  empty states, and range switches do not. Removed users disappear immediately without delaying
  polling or retaining stale links. Reduced motion disables all leaderboard animations and
  cancels any active transitions. Browsers without Web Animations update immediately.
