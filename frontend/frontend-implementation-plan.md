# Frontend Implementation Plan

> Implementation conventions source: `frontend/.github/copilot-instructions.md`

## A. Project Overview

### Purpose and Scope

Build a polished, responsive React developer-community frontend for:

- Authentication and account management
- Public posts and comments
- Authenticated likes and unlike actions
- Public leaderboard
- Authenticated user directory and public profiles
- Protected admin user management

Do not implement password reset, follow/unfollow, notifications, search, user-post history, like counts, or liked-state APIs because the backend does not provide them.

### Technology

- React
- TypeScript
- Vite
- React Router
- Axios
- Tailwind CSS
- Lucide React
- Vitest
- React Testing Library

Before choosing or upgrading dependencies, verify installed Node.js compatibility and build a clean installation. Use stable mutually compatible releases.

### Backend Communication

- Development gateway origin: `http://localhost:8081`
- Production base URL: `VITE_API_BASE_URL`
- All browser traffic goes through the API Gateway.
- Never call microservice ports or expose secrets in frontend code.
- Use one centralized Axios client and typed endpoint modules.
- Keep access tokens in memory and refresh tokens only in `sessionStorage`.
- Do not use `localStorage`, HTTP-only-cookie assumptions, or browser-managed authentication.

### Architectural Decisions

- Use feature-oriented folders while keeping shared UI components small.
- Separate API calls, DTO types, authentication state, routing guards, and UI components.
- Centralize request/response types, interceptors, error normalization, and pagination handling.
- Decode JWT `sub` and `role` only for UI identity and convenience restrictions.
- Treat gateway authorization and backend validation as authoritative.

## B. Frontend Project Structure

```text
frontend/
├── src/
│   ├── api/
│   │   ├── client.ts
│   │   ├── errors.ts
│   │   ├── auth.ts
│   │   ├── posts.ts
│   │   ├── comments.ts
│   │   ├── likes.ts
│   │   ├── users.ts
│   │   ├── admin.ts
│   │   └── leaderboard.ts
│   ├── auth/
│   │   ├── AuthContext.tsx
│   │   ├── authState.ts
│   │   ├── jwt.ts
│   │   └── refreshManager.ts
│   ├── components/
│   │   ├── layout/
│   │   ├── feedback/
│   │   ├── navigation/
│   │   ├── forms/
│   │   └── ui/
│   ├── features/
│   │   ├── auth/
│   │   ├── posts/
│   │   ├── comments/
│   │   ├── likes/
│   │   ├── leaderboard/
│   │   ├── users/
│   │   └── admin/
│   ├── pages/
│   │   ├── auth/
│   │   ├── posts/
│   │   ├── users/
│   │   ├── account/
│   │   ├── leaderboard/
│   │   └── admin/
│   ├── hooks/
│   ├── types/
│   ├── utils/
│   ├── router/
│   │   ├── AppRouter.tsx
│   │   └── guards.tsx
│   ├── styles/
│   ├── test/
│   └── main.tsx
├── public/
├── .env.example
├── package.json
├── vite.config.ts
└── tailwind.config.ts
```

Keep abstraction limited to reusable UI, API, authentication, and routing concerns.

## C. Design System

### Palette

- Background: `#0F1117`
- Surface: `#171A23`
- Elevated surface: `#1C202C`
- Border: `#2A2F3D`
- Primary: `#7C3AED`
- Primary hover: `#8B5CF6`
- Text: `#F4F4F5`
- Muted text: `#A1A1AA`
- Subtle text: `#71717A`
- Success: `#22C55E`
- Warning: `#F59E0B`
- Error: `#EF4444`
- Focus ring: `#A78BFA`

### Typography and Spacing

- System sans-serif typography
- Page title: 30–36px
- Section title: 20–24px
- Card title: 16–18px
- Body: 14–16px
- Metadata: 12–13px
- Spacing scale: 4, 8, 12, 16, 24, 32, 48px
- Radius: 6px controls, 8px medium controls, 12px cards, 16px dialogs

### Components and States

- Provide primary, secondary, ghost, danger, and disabled buttons.
- Inputs include visible labels, limits, field errors, focus rings, and disabled states.
- Cards use restrained shadows and subtle borders.
- Dialogs and confirmations use semantic headings and focus containment.
- Navigation supports active, hover, disabled, loading, and mobile states.
- Badges communicate role or status only from confirmed backend/JWT data.
- Use visible keyboard focus and semantic HTML.
- Give every icon-only control an accessible label.

### Responsive Layout

- Breakpoints: 640px, 768px, 1024px, 1280px
- Desktop: side navigation and wider content cards
- Tablet: compact navigation and two-column layouts where useful
- Mobile: header navigation, stacked forms, and full-width dialogs

## D. Application Pages and Routing

### `/login`

- Purpose: authenticate an existing user.
- Access: unauthenticated guests only.
- API: `POST /api/auth/login` with `{ login, password }`.
- States: loading, invalid credentials, field validation, network failure.
- Authenticated users should redirect to `/posts`.

### `/register`

- Purpose: create an account.
- Access: unauthenticated guests only.
- API: `POST /api/auth/register` with `{ username, email, password }`.
- Enforce username 50, email 254, and password 72-character limits.
- Success redirects to `/login` after a confirmed `201` response.

### `/posts`

- Purpose: public paginated posts feed.
- API: `GET /api/posts` and, for authenticated users, `POST /api/posts`.
- Create body: `{ content }`, maximum 255 characters.
- Owner or `ADMIN` users may edit/delete posts.
- States: loading, empty, pagination, 403/404, mutation error, network retry.

### `/posts/:postId`

- Purpose: public post detail and comments.
- API:
  - `GET /api/posts/{id}`
  - `GET /api/posts/{postId}/comments`
  - `POST /api/posts/{postId}/comments`
  - Post/comment like and unlike endpoints
- Comment content maximum 1000 characters.
- Owner or `ADMIN` users may edit/delete comments.
- Do not display counts or an unknown liked state.

### `/leaderboard`

- Purpose: public top-10/top-100 leaderboard.
- API:
  - `GET /api/leaderboard/top-10`
  - `GET /api/leaderboard/top-100`
- Rank is array index plus one; there is no separate ranking field.
- Entries link to `/users/{userId}`.
- States: loading, empty, network retry.

### `/users`

- Purpose: authenticated paginated user directory.
- API: `GET /api/users?page={page}&size={size}&sort={sort}`.
- Link users to `/users/{id}`.
- States: loading, empty, pagination, 403, network retry.

### `/users/:id`

- Purpose: authenticated public profile lookup.
- API: `GET /api/users/{id}`.
- Display only fields confirmed by `UserResponse`.
- Do not imply user-specific posts or like statistics.

### `/account`

- Purpose: account settings for the JWT subject.
- Get profile using JWT `sub` and `GET /api/users/{id}` because `GET /api/users/me` does not exist.
- API:
  - `PATCH /api/users/me`
  - `PATCH /api/users/me/password`
  - `DELETE /api/users/me`
- Require confirmation for deletion and clear auth state after success.

### `/admin`

- Purpose: protected admin user management.
- Access: JWT role `ADMIN` for the client guard; gateway authorization remains authoritative.
- API:
  - `GET /api/users`
  - `POST /api/admin/users`
  - `PATCH /api/admin/users/{id}`
  - `DELETE /api/admin/users/{id}`
  - `PATCH /api/admin/users/{id}/password`
  - `PATCH /api/admin/users/{id}/role`

### `/403`

- Purpose: forbidden-access explanation with navigation to allowed pages.

### `/404`

- Purpose: unknown route and confirmed resource-not-found handling.

## E. API Integration Checklist

### Authentication

- `POST /api/auth/register` — `{ username, email, password }` → `201 UserResponse` — public
- `POST /api/auth/login` — `{ login, password }` → `200 TokenResponse` — public
- `POST /api/auth/refresh` — `{ refreshToken }` → `200 TokenResponse` — public; excluded from refresh interceptor
- `POST /api/auth/logout` — `{ refreshToken }` → `204` — public endpoint; excluded from refresh interceptor

### Posts

- `GET /api/posts` — Spring Pageable parameters → `200 Page<PostDTO>` — public
- `GET /api/posts/{id}` → `200 PostDTO` — public
- `POST /api/posts` — `{ content }` → `201 PostDTO` — authenticated
- `PUT /api/posts/{id}` — `{ content }` → `200 PostDTO` — owner or `ADMIN`
- `DELETE /api/posts/{id}` → `204` — owner or `ADMIN`

### Comments

- `GET /api/posts/{postId}/comments` — Spring Pageable parameters → `200 Page<CommentDTO>` — public
- `GET /api/posts/{postId}/comments/{commentId}` → `200 CommentDTO` — public
- `POST /api/posts/{postId}/comments` — `{ content }` → `201 CommentDTO` — authenticated
- `PUT /api/posts/{postId}/comments/{commentId}` — `{ content }` → `200 CommentDTO` — owner or `ADMIN`
- `DELETE /api/posts/{postId}/comments/{commentId}` → `204` — owner or `ADMIN`

### Likes

- `POST /api/posts/{postId}/likes` — no body → `201 LikeDTO` — authenticated
- `DELETE /api/posts/{postId}/likes` — no body → `204` — authenticated
- `POST /api/posts/{postId}/comments/{commentId}/likes` — no body → `201 LikeDTO` — authenticated
- `DELETE /api/posts/{postId}/comments/{commentId}/likes` — no body → `204` — authenticated

### Users

- `GET /api/users/{id}` → `200 UserResponse` — authenticated
- `GET /api/users` — `page`, `size`, `sort` → `200 PageResponse<UserResponse>` — authenticated
- `PATCH /api/users/me` — `{ username?, email? }` → `200 UserResponse` — authenticated
- `PATCH /api/users/me/password` — `{ currentPassword, newPassword }` → `200` — authenticated
- `DELETE /api/users/me` → `204` — authenticated

### Admin

- `POST /api/admin/users` — `{ username, email, password, role }` → `201 UserResponse` — `ADMIN`
- `PATCH /api/admin/users/{id}` — `{ username?, email? }` → `200 UserResponse` — `ADMIN`
- `DELETE /api/admin/users/{id}` → `204` — `ADMIN`
- `PATCH /api/admin/users/{id}/password` — `{ newPassword }` → `204` — `ADMIN`
- `PATCH /api/admin/users/{id}/role` — `{ role: "USER" | "ADMIN" }` → `200 UserResponse` — `ADMIN`

### Leaderboard

- `GET /api/leaderboard/top-10` → `200 LeaderboardEntryDTO[]` — public
- `GET /api/leaderboard/top-100` → `200 LeaderboardEntryDTO[]` — public

### Pagination DTO Verification

The User Service explicitly defines and returns `PageResponse<T>`:

```ts
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
```

For `GET /api/users`, use `PageResponse<UserResponse>`.

Posts and comments use Spring `Page<T>` responses. Type only fields required by UI:

```ts
interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  pageable?: unknown;
  sort?: unknown;
  numberOfElements?: number;
}
```

Do not invent application-specific fields.

### Axios and Errors

- Create one centralized Axios instance in `src/api/client.ts`.
- Use `VITE_API_BASE_URL` for production and `http://localhost:8081` for development.
- Attach `Authorization: Bearer <accessToken>` when the access token exists.
- Request interceptors must not process auth endpoints listed as refresh exclusions.
- Centralize DTO models, endpoint methods, and normalized error extraction.
- Preserve HTTP status and backend messages.
- Extract Post Service `fieldErrors` for inline forms.
- Map 401 to refresh/logout behavior, 403 to forbidden UI, and 404 to not-found UI.
- Render concise retryable network-failure messages.
- Never log or expose access or refresh tokens.

## F. Authentication and Security Flow

### Startup Authentication Restoration

1. Initialize auth state as `restoring`.
2. Keep the access token only in memory.
3. At application startup, read the refresh token from `sessionStorage`, if present.
4. If no refresh token exists, finish startup as unauthenticated.
5. If a refresh token exists, attempt exactly one refresh with `POST /api/auth/refresh`.
6. Send `{ refreshToken: storedRefreshToken }`.
7. On success, replace the in-memory access token and refresh token with the returned rotated values.
8. Immediately replace the old persisted refresh token with the returned `refreshToken` in `sessionStorage`.
9. Restore authenticated state only after the refresh succeeds and JWT `sub`/`role` are parsed.
10. If startup refresh fails, clear the in-memory access token, in-memory refresh token, and persisted refresh token.
11. Enter unauthenticated state and show the normal unauthenticated application.
12. Prevent protected routes from rendering until restoration is complete.

### Login

1. Submit `{ login, password }` to `POST /api/auth/login`.
2. Store `accessToken` in memory only.
3. Persist `refreshToken` in `sessionStorage` only.
4. Set authenticated state and redirect to `/posts`.

### Registration

1. Submit `{ username, email, password }` to `POST /api/auth/register`.
2. Handle `201 UserResponse` as successful registration.
3. Do not create an authenticated session unless an explicit login succeeds.

### 401 Refresh Flow

- Implement one shared refresh promise for concurrent 401 responses.
- Mark every original request explicitly as already retried.
- Retry an original request at most once.
- Exclude login, registration, refresh, and logout endpoints from refresh handling.
- Replace both tokens immediately after a successful refresh rotation.
- If refresh fails, clear all auth state and redirect to `/login`.

### Logout

1. Call `POST /api/auth/logout` with the current refresh token.
2. Clear the access token, refresh token, and persisted refresh token in every outcome.
3. Redirect to `/login`.

### Protected Routes

- Guest routes allow only unauthenticated users.
- Protected routes block rendering until startup restoration finishes.
- Admin routes require decoded role `ADMIN` for UI convenience only.
- Post/comment ownership compares numeric JWT `sub` with `userId`.
- Gateway checks remain the enforcement authority.

## G. LikeButton Behavior

- The backend exposes no current-user liked state.
- The backend exposes no like count.
- Do not implement a misleading toggle based on unknown initial state.
- Do not fabricate counts, selected states, or reload-restorable liked state.
- Use separate “Like” and “Unlike” actions instead of an inferred toggle.
- Disable only the relevant pending action.
- Do not update state optimistically.
- Update local confirmed UI only after a successful API response.
- Handle duplicate-like responses by displaying a concise normalized message.
- Handle invalid-unlike responses by displaying a concise normalized message and retaining confirmed UI state.
- A successful like is confirmation of `LikeDTO`; a successful unlike confirms removal only.

## H. Component Design

- **AppShell:** shared navigation, account menu, mobile behavior, and page outlet.
- **ProtectedRoute:** prevents protected rendering during and after failed startup restoration.
- **GuestRoute:** redirects authenticated users away from login/register.
- **AdminRoute:** convenience role guard for `role === "ADMIN"`.
- **PostCard:** confirmed fields and owner/admin mutations.
- **PostComposer:** 255-character post creation form.
- **PostDetail:** public post data and comments.
- **CommentList:** paginated comments.
- **CommentComposer:** 1000-character comment form.
- **LikeActions:** separate confirmed like/unlike operations.
- **LeaderboardTable:** array-position ranks and profile links.
- **UserTable:** confirmed directory fields.
- **UserProfile:** confirmed public profile fields.
- **AccountSettings:** profile, password, and account deletion flows.
- **AdminUserTable:** directory plus admin mutation dialogs.
- **Dialog/ConfirmDialog:** accessible confirmation and forms.
- **Alert/FieldError/Spinner/EmptyState:** consistent feedback components.

## I. Implementation Phases

### Phase 1: Project Initialization and Tooling

**Goal:** Create a clean, verified React/TypeScript foundation.

**Create:**
- Vite project
- Strict TypeScript configuration
- Tailwind configuration
- React Router
- Axios
- Vitest and React Testing Library
- Environment example file

**Implement:**
- Package scripts
- Gateway base URL configuration
- Basic app entry point

**Tests:**
- Initial test environment smoke test

**Acceptance:**
- Clean install succeeds
- TypeScript and production build succeed
- Axios packages and versions are compatible with the installed Node.js version

**Dependencies:** None.

### Phase 2: Global Styles and Design System

**Goal:** Establish the reusable visual foundation.

**Create:**
- Tailwind theme tokens
- Base body/page styles
- Shared Button, Input, Card, Badge, Dialog, Alert, Spinner, and EmptyState components
- Responsive AppShell styles

**Implement:**
- Dark palette
- Focus, hover, disabled, loading, and error variants
- Mobile breakpoints

**Tests:**
- Meaningful interaction tests for shared controls where behavior exists

**Acceptance:**
- Components consistently match the palette and typography
- Keyboard focus and icon-only labels are present

**Dependencies:** Phase 1.

### Phase 3: Axios Client and API Models

**Goal:** Build the typed gateway integration layer.

**Create:**
- `src/api/client.ts`
- `src/api/errors.ts`
- Exact auth, post, comment, like, user, admin, and leaderboard DTO types
- Endpoint modules for all confirmed routes
- `SpringPage<T>` and `PageResponse<T>` types

**Implement:**
- Central gateway base URL
- Authorization interceptor
- API error normalization
- Pagination parameter/query helpers

**Tests:**
- Endpoint paths, methods, bodies, and query parameters
- Error normalization and status preservation
- Authorization header behavior

**Acceptance:**
- Every application API call goes through the gateway client
- No endpoint or DTO field not confirmed by source is present

**Dependencies:** Phase 1.

### Phase 4: Authentication and Route Protection

**Goal:** Implement secure session behavior and startup restoration.

**Create:**
- Auth context and auth state
- JWT claim parser
- Refresh manager
- Session refresh-token storage
- Guest, protected, and admin route guards

**Implement:**
- Login and registration
- Shared refresh promise
- Exactly-one startup refresh
- Refresh-token rotation
- Concurrent 401 handling
- Logout cleanup
- Protected-page restoration loading state

**Tests:**
- Login/registration
- Startup restoration success/failure
- Refresh rotation
- Shared refresh promise
- Retry limit
- Auth endpoint exclusions
- Protected and admin route guards

**Acceptance:**
- Access token stays in memory
- Refresh token exists only in `sessionStorage`
- Protected content is hidden until restoration finishes
- Refresh failures return to unauthenticated state

**Dependencies:** Phases 1–3.

### Phase 5: Application Layout and Navigation

**Goal:** Add the application shell and navigation.

**Create:**
- AppShell
- Desktop/mobile navigation
- Account menu
- Not-found and forbidden pages

**Implement:**
- Public/private navigation
- Admin navigation for `ADMIN` only
- Loading shell during auth restoration

**Tests:**
- Route rendering and navigation guards
- Mobile menu and icon-label behavior

**Acceptance:**
- Navigation exposes only supported routes and convenience permissions

**Dependencies:** Phase 4.

### Phase 6: Posts Feed and Post Details

**Goal:** Implement public and protected post flows.

**Create:**
- PostCard
- PostComposer
- PostDetail
- Edit/delete dialogs

**Implement:**
- Public pagination
- Post creation
- Post update/delete
- Numeric owner/admin action visibility

**Backend endpoints:**
- `GET /api/posts`
- `GET /api/posts/{id}`
- `POST /api/posts`
- `PUT /api/posts/{id}`
- `DELETE /api/posts/{id}`

**Tests:**
- Pagination, creation, edit/delete, ownership, admin UI, and errors

**Acceptance:**
- Public reading works without authentication
- Mutations require authentication
- No unsupported post metadata is fabricated

**Dependencies:** Phases 3–5.

### Phase 7: Comments and Likes

**Goal:** Implement comments and honest like/unlike actions.

**Create:**
- CommentList
- CommentItem
- CommentComposer
- Separate LikeActions controls

**Implement:**
- Comment pagination and CRUD
- Confirmed like/unlike responses
- Duplicate and invalid-action warnings
- Owner/admin edit/delete controls

**Backend endpoints:**
- Comment list/detail/create/update/delete
- Post and comment like/unlike endpoints

**Tests:**
- Comment CRUD
- Like 201 behavior
- Unlike 204 behavior
- Duplicate-like and invalid-unlike handling
- No optimistic toggle/count behavior

**Acceptance:**
- No like counts or unknown liked state are shown
- Local UI changes only after confirmed responses

**Dependencies:** Phase 6.

### Phase 8: Leaderboard

**Goal:** Display supported leaderboard data.

**Create:**
- Leaderboard table/list
- Loading and empty states

**Implement:**
- Top-10/top-100 retrieval
- Array-index rank
- Profile navigation

**Tests:**
- Rankings and profile links
- Empty/error/loading states

**Acceptance:**
- No fabricated ranking fields are displayed

**Dependencies:** Phase 5.

### Phase 9: User Directory and Profiles

**Goal:** Implement authenticated user browsing.

**Create:**
- Paginated UserTable
- UserProfile page

**Implement:**
- `PageResponse<UserResponse>` pagination
- Public profile retrieval

**Tests:**
- Pagination fields
- Profile retrieval
- 403/network states

**Acceptance:**
- No user-post history or unsupported profile fields are shown

**Dependencies:** Phases 4–5.

### Phase 10: Account Settings

**Goal:** Implement supported account management.

**Create:**
- Profile form
- Password form
- Account deletion confirmation

**Implement:**
- Retrieve profile using JWT `sub`
- Update profile
- Change password
- Delete account and logout

**Tests:**
- Validation
- Profile update
- Password 200 response
- Account deletion 204 and auth cleanup

**Acceptance:**
- No `GET /api/users/me` request is made
- Refresh/logout state is cleared correctly

**Dependencies:** Phase 9.

### Phase 11: Admin Dashboard

**Goal:** Implement protected admin user management.

**Create:**
- Paginated admin user table
- Create/edit/password/role dialogs
- Deletion confirmation

**Implement:**
- All confirmed admin endpoints
- Role convenience guard

**Tests:**
- Non-admin route protection
- Create/update/delete/password/role requests
- Admin password 204 and admin create 201 handling

**Acceptance:**
- Admin UI is hidden for non-admin users
- Gateway remains authoritative

**Dependencies:** Phase 9.

### Phase 12: Responsive and Accessibility Refinement

**Goal:** Complete quality and responsive behavior.

**Implement:**
- Mobile navigation and stacked layouts
- Dialog focus behavior
- Text contrast and field labels
- Loading/empty/error consistency
- Reduced motion where applicable

**Tests:**
- Keyboard and accessibility-focused component tests
- Responsive manual verification

**Acceptance:**
- Major flows work on mobile, tablet, and desktop
- Icon-only controls have labels
- Visible focus and semantic headings exist

**Dependencies:** All feature phases.

### Phase 13: Integration Testing and Final Verification

**Goal:** Verify the complete frontend against the running gateway.

**Implement:**
- Gateway-backed manual test matrix
- Final cleanup and documentation

**Tests:**
- Unit tests
- Component tests
- API client tests
- Auth and route guard tests
- Critical integration flows

**Acceptance:**
- Clean install
- No strict TypeScript errors
- Vitest suite passes
- Production build succeeds
- All browser requests use API Gateway
- CORS origin is explicitly configured for manual gateway verification

**Dependencies:** All previous phases.

## J. Testing Strategy

- Unit-test JWT parsing, session refresh-token storage, pagination mappers, validation, and errors.
- Component-test login, registration, forms, dialogs, feed cards, comments, and feedback.
- API-test every method, path, body, and parameter.
- Auth-test login, registration, startup restore, refresh rotation, concurrent refresh, logout, and refresh failure.
- Route-test guest, protected, owner, and admin behavior.
- Integration-test critical feed, comments, likes, user directory, account, and admin flows.
- Manual-test against a running API Gateway with a configured CORS origin.

## K. Known Backend Limitations

- `GET /api/users/me` does not exist; account profile retrieval uses JWT `sub` plus `GET /api/users/{id}`.
- No user-specific posts endpoint exists.
- No like counts or current-user liked-state fields exist.
- No password-reset endpoint or email-reset flow exists.
- No follow/unfollow, notifications, direct post search, or comment-like update endpoint exists.
- The backend does not set HTTP-only cookies or a browser-managed session.
- CORS has no default frontend origin and requires gateway configuration.
- Frontend UI must not fabricate unsupported rankings, counts, states, profiles, or history.

## L. Final Definition of Done

- All supported backend features are implemented.
- Every browser request routes through API Gateway.
- Access tokens remain in memory.
- Refresh tokens are persisted only in `sessionStorage`.
- Startup refresh restoration succeeds, rotates, fails safely, and blocks protected routes appropriately.
- Concurrent refresh and retry behavior is safe and limited.
- Owner/admin UI restrictions match backend capabilities.
- User directory pagination uses verified `PageResponse<UserResponse>` fields.
- Like UI does not infer counts or liked state.
- No unsupported backend capabilities are fabricated.
- Responsive dark UI, keyboard controls, labels, and contrast are complete.
- Unit, component, API, auth, route, and critical integration tests pass.
- Strict TypeScript reports no errors.
- Clean install and production build succeed.

