# GitHub Copilot Instructions: Frontend

## Scope

Implement a React frontend only. Do not modify or add backend code, API Gateway code, Docker configuration, or private keys/secrets.

## Technology

- React
- TypeScript
- Vite
- React Router
- Axios
- Tailwind CSS
- Lucide React

Before selecting or upgrading package versions, check the installed Node.js version and verify that every chosen package supports it. Use stable, mutually compatible releases rather than pinning arbitrary major/minor versions. After dependency changes, verify the Vite, React, Router, Axios, Tailwind, and Lucide versions work together through a clean install and build.

## Visual Direction

Build a polished dark developer-community social platform:

- Graphite background, slightly lighter card/panel surfaces, subtle borders and restrained shadows.
- Violet/indigo primary accents; muted text for secondary information.
- Clear typography, visual hierarchy, consistent spacing, radii, and component variants.
- Restrained hover/focus transitions; visible keyboard focus rings.
- Responsive desktop, tablet, and mobile layouts.
- Accessible contrast and semantic HTML. Every icon-only control needs an accessible label.
- Prefer clean layouts and brief transitions. Avoid excessive gradients, glow, animation, overcrowding, and generic Bootstrap-like styling.

Use Tailwind utilities consistently. Extract only reusable components that reduce duplication; do not introduce premature abstraction.

## API and Architecture

All browser API traffic must go through API Gateway:

- Development base URL: `http://localhost:8081`
- Never call post, user, leaderboard, or other microservice ports directly.
- Never expose private keys, public-key configuration secrets, credentials, or backend secrets in frontend code.
- Put production base URLs in environment variables, for example `VITE_API_BASE_URL`.
- Configure a single centralized Axios instance in `src/api/`.
- Centralize request types, response types, endpoint functions, interceptors, and error normalization. UI components must not duplicate request logic.
- Use request interceptors to attach `Authorization: Bearer <accessToken>` when an access token exists.
- Implement refresh-on-401 with a shared refresh promise so concurrent 401 responses share one refresh request. Retry an original request at most once, mark it explicitly as already retried, and do not allow an auth endpoint or retried request to enter another refresh attempt. Exclude login, registration, refresh, and logout endpoints from automatic refresh handling.
- If refresh fails, clear authentication state and persisted refresh tokens, prevent further retries, and redirect to login. Handle refresh-token rotation by replacing the old refresh token with the returned `refreshToken` immediately.
- Never log, print, or expose access or refresh tokens in error messages, console output, analytics, or UI state/debug output.

CORS is not enabled by default. The gateway permits configured origins only; frontend environment setup must not assume an origin is already allowed. The gateway allows `Authorization`, `Content-Type`, and `Accept` headers.

## Confirmed Backend API

Base every implementation on these actual routes. Do not assume routes not listed here.

### Authentication

- `POST /api/auth/register` — body: `username`, `email`, `password`; returns `UserResponse`; HTTP 201.
- `POST /api/auth/login` — body: `login`, `password`; returns `TokenResponse`; HTTP 200.
- `POST /api/auth/refresh` — body: `{ "refreshToken": "<token>" }`; returns `TokenResponse`; HTTP 200.
- `POST /api/auth/logout` — body: `{ "refreshToken": "<token>" }`; returns no content; HTTP 204.

`TokenResponse` is:

```ts
{
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}
```

`expiresIn` is seconds. The access-token TTL is 900 seconds; refresh tokens expire after 30 days in the current configuration.

The access JWT contains numeric positive `sub` as the user ID and a `role` claim containing `USER` or `ADMIN`. The gateway validates issuer, audience, subject, role, and expiration. It adds trusted `X-User-Id` and, where required, `X-User-Role` headers downstream.

`RegisterRequest`: `username` max 50 characters, `email` max 254 and valid email, `password` max 72 bytes/characters as supported by backend validation.

`LoginRequest`: `login` max 254 characters and `password` max 72 characters.

Refresh behavior rotates the refresh token: every successful refresh revokes the submitted token and returns a replacement refresh token. Logout revokes the submitted refresh token. The backend does not set HTTP-only cookies or provide a browser-managed session. The frontend must therefore keep the access token and refresh token according to the application's explicit security decision; never hardcode credentials/tokens.

Store the access token only in memory. Never store access or refresh tokens in `localStorage`. Prefer `sessionStorage` for refresh-token persistence only when browser-session persistence is sufficient; browser-accessible storage is vulnerable to XSS and may expose stored tokens to injected scripts. Do not read/write hardcoded secrets. Clear the in-memory access token, refresh token, and any persisted refresh token on logout or refresh failure. Coordinate concurrent refresh attempts to avoid using the same rotating refresh token more than once. Decode JWT claims only for UI state such as role/user ID; backend/gateway authorization remains authoritative.

### Public Routes

Gateway-permitted public requests:

- `GET /api/posts`
- `GET /api/posts/{id}`
- `GET /api/posts/{postId}/comments`
- `GET /api/posts/{postId}/comments/{commentId}`
- `GET /api/posts/{postId}/engagement`
- `GET /api/posts/{postId}/comments/{commentId}/engagement`
- `GET /api/users/{id}/summary`
- `GET /api/leaderboard/top-10`
- `GET /api/leaderboard/top-100`

The posts and comments list endpoints return Spring `Page` response shapes and the frontend requests five items per page. Type the pagination fields (`content`, `pageable` may exist in Spring responses) only if implementing endpoints against the actual response; do not invent custom pagination fields. Engagement summary GETs are public and return counts to anonymous readers. The public user summary route returns only `id` and `username`; full profiles and all other user routes require authentication.

### Posts and Comments

- `POST /api/posts` body `{ content }` max 255; returns `PostDTO`.
- `GET /api/posts` paginated; public.
- `GET /api/posts/{id}`; public.
- `PUT /api/posts/{id}` body `{ content }` max 255; owner or `ADMIN`.
- `DELETE /api/posts/{id}`; owner or `ADMIN`.

`PostDTO`:

```ts
{
  id: number;
  content: string;
  userId: number;
  createdAt: string;
  updatedAt: string;
}
```

- `POST /api/posts/{postId}/comments` body `{ content }` max 1000; returns `CommentDTO`.
- `GET /api/posts/{postId}/comments` paginated; public.
- `GET /api/posts/{postId}/comments/{commentId}`; public.
- `PUT /api/posts/{postId}/comments/{commentId}` body `{ content }` max 1000; owner or `ADMIN`.
- `DELETE /api/posts/{postId}/comments/{commentId}`; owner or `ADMIN`.

`CommentDTO`:

```ts
{
  id: number;
  content: string;
  userId: number;
  postId: number;
  createdAt: string;
  updatedAt: string;
}
```

### Likes and engagement summaries

- `POST /api/posts/{postId}/likes` — authenticated; returns `LikeDTO`.
- `DELETE /api/posts/{postId}/likes` — authenticated; no content.
- `POST /api/posts/{postId}/comments/{commentId}/likes` — authenticated; returns `LikeDTO`.
- `DELETE /api/posts/{postId}/comments/{commentId}/likes` — authenticated; no content.
- `GET /api/posts/{postId}/engagement` — public; returns `{ likeCount, commentCount, likedByMe }`.
- `GET /api/posts/{postId}/comments/{commentId}/engagement` — public; returns `{ likeCount, likedByMe }`.

`LikeDTO`:

```ts
{
  id: number;
  userId: number;
  postId: number | null;
  commentId: number | null;
  createdAt: string;
}
```

The summary routes expose confirmed read-only counts to everyone. Anonymous responses use `likedByMe: null`; authenticated responses use `true` or `false`. Do not infer or fabricate counts or liked state. Like and Unlike controls must use the confirmed summary state; Unlike removes an existing like and is not a separate reaction. Refresh the summary after successful mutations and duplicate-like/missing-like responses. Keep actions disabled while state is unknown, loading, unavailable, or a mutation is pending. Discard stale responses when the resource or viewer changes. Do not change existing Like/Unlike, self-like, authorization, deletion, or leaderboard behavior.

### Users

- `GET /api/users/{id}` — authenticated public-profile lookup.
- `GET /api/users` — authenticated paginated user directory. Spring `Pageable` parameters (`page`, `size`, `sort`) are supported.
- `PATCH /api/users/me` — body fields `username?` and `email?`; returns `UserResponse`.
- `DELETE /api/users/me` — deletes current account; no content.
- `PATCH /api/users/me/password` — body `{ currentPassword, newPassword }`; no content.

`UserResponse`:

```ts
{
  id: number;
  username: string;
  email: string;
  role: "USER" | "ADMIN";
  registeredAt: string;
  lastLoginAt: string | null;
  updatedAt: string;
}
```

`GET /api/users/me` does not exist. There is no endpoint for posts by a specific user and no current-user endpoint for profile retrieval.

### Leaderboard

- `GET /api/leaderboard/top-10` — public; returns `LeaderboardEntryDTO[]`.
- `GET /api/leaderboard/top-100` — public; returns `LeaderboardEntryDTO[]`.

```ts
{
  userId: number;
  username: string;
  points: number;
}
```

Display list position as the array index plus one. Navigation to `/users/{userId}` is supported by the authenticated profile endpoint. The backend does not provide ranking fields separate from array position.

### Admin

Admin routes are restricted by gateway role checks for JWT `role: "ADMIN"`:

- `POST /api/admin/users` — body `username`, `email`, `password`, `role`.
- `PATCH /api/admin/users/{id}` — body `username?`, `email?`.
- `DELETE /api/admin/users/{id}`.
- `PATCH /api/admin/users/{id}/password` — body `{ newPassword }`.
- `PATCH /api/admin/users/{id}/role` — body `{ role: "USER" | "ADMIN" }`.

Hide admin navigation/actions for non-admin users and protect routes client-side, but always rely on the gateway for enforcement.

## Protected Routes and UI Permissions

Use React Router route guards for:

- Login/register for unauthenticated users.
- Protected pages for authenticated users.
- Admin pages for `role === "ADMIN"`.

Do not expose privileged controls solely from JWT parsing. UI permissions are convenience only. Owner checks use the numeric JWT subject and post/comment `userId`/author IDs. Admins may edit/delete posts and comments; regular users may edit/delete only their own content.

## Missing or Unclear Backend Capabilities

Do not implement or imply the following:

- No `GET /api/users/me` endpoint.
- No endpoint to retrieve posts for a specific user.
- Post/comment DTOs do not include engagement counts; use the read-only summary routes above.
- No endpoint to update or delete a single comment like except through the implemented post/comment like routes.
- No endpoint to reset password by email or any password-reset flow.
- No social follow/unfollow endpoint.
- No notifications endpoint.
- No direct post search endpoint.
- No frontend origin is configured by default in CORS.
- No HTTP-only refresh-token cookie or server-side browser session exists.
- No backend contract has confirmed a standardized response shape for all services; handle known DTOs and normalize service-specific errors.

## TypeScript and Code Quality

- Enable strict TypeScript and avoid `any`; isolate unavoidable unknown external shapes with narrowing.
- Model DTOs exactly around the backend fields above.
- Use discriminated union types or typed state for loading/error/success states where useful.
- Follow SOLID, DRY, KISS, and YAGNI. Keep components small and purposeful.
- Separate API calls, typed models, auth state, routing guards, and UI components.
- Avoid giant components, duplicated business logic, unnecessary global state, and premature abstractions.
- Use Axios for all HTTP requests through the centralized client; do not use the native Fetch API for application data calls.
- Format timestamps and pagination consistently. Do not mutate API objects; clone or map as needed.

## Error Handling

Support consistent error states. Known backend error shapes differ:

- User Service: `timestamp`, `status`, `error`, `message`, `path`.
- Post Service: `timestamp`, `status`, `error`, `message`, `path`, `fieldErrors`.
- Validation: honor backend field constraints in forms and display field errors when available.
- Email forms should retain the accessible guidance `Enter a valid email address, for example name@example.com.` alongside required, overlength, duplicate-email, and server-side errors.

Centralize Axios error extraction, preserve HTTP status, and map 401 to authentication refresh/logout handling, 403 to forbidden UI, and 404 to not-found UI. Network failures should show a concise retryable message.

## Testing

Use Vitest and React Testing Library for meaningful coverage:

- Authentication login/registration, refresh, refresh-token rotation, logout, and storage-state behavior.
- Protected and role-based route guards.
- Centralized Axios authorization and refresh interceptor behavior.
- API request parameter/body correctness and error normalization.
- Critical post/comment create/edit/delete and like flows.
- Important user/profile/admin interactions.

Do not require snapshots or tests for trivial presentational components without meaningful behavior.

From `frontend/`, use `npm test` for the full suite, `npm run lint` for the TypeScript project check (`tsc -b --pretty false`), and `npm run build` for type-checking plus the Vite production build. Posts and comments use five-item pages; keep regression coverage for creation/deletion refreshes, page fallback, preserved drafts, and stale-request protection. Profile date output is explicitly `en-US`. Reaction success/benign-warning feedback fades only during the final 400 ms of its 4-second lifetime using motion-safe styles; genuine errors persist.
