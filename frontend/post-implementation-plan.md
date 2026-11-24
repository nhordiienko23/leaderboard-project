# Post/comment presentation fixes

## 1. Problem and agreed scope

Improve post and comment presentation while preserving all existing business behavior.

Current problems:
- Email validation does not explain the expected format.
- Reaction feedback remains visible indefinitely.
- Reaction controls have no visible icons or reload-restorable state.
- Post and comment like counts are not displayed.
- Public-profile dates depend on the browser locale.
- Post and comment pages currently request ten items.

The user approved a small read-only backend and gateway extension because existing DTOs and endpoints do not expose total likes or the current viewer's liked state.

### Non-negotiable constraints

- Preserve all existing Like/Unlike mutations, authorization, self-like rules, deletion behavior, and leaderboard scoring.
- ThumbsDown means **Unlike / remove an existing like**, not a separate negative reaction.
- Do not add dislike persistence, dislike counts, or dislike scoring.
- Do not modify existing PostDTO/CommentDTO or mutation API contracts.
- Do not introduce schema migrations, Kafka/outbox changes, or unrelated business logic changes.
- Preserve the existing dirty worktree, especially frontend files and gateway identity/security changes.
- Inspect existing implementations and conventions before editing. Reuse existing mechanisms rather than introducing parallel abstractions.
- Do not overwrite, reset, revert, or discard unrelated user changes.

## 2. Exposing read-only engagement data

Add read-only engagement summary endpoints.

### Proposed routes

- `GET /api/posts/{postId}/engagement`
- `GET /api/posts/{postId}/comments/{commentId}/engagement`

### Response contracts

Post:

```typescript
interface PostEngagementResponse {
  likeCount: number;
  commentCount: number;
  likedByMe: boolean | null;
}
```

Comment:

```typescript
interface CommentEngagementResponse {
  likeCount: number;
  likedByMe: boolean | null;
}
```

### Contract rules

- Counts must never be negative.
- Anonymous requests return `likedByMe: null`.
- Authenticated requests return `likedByMe: true` or `false`.
- Counts are returned to both anonymous and authenticated readers.
- `commentCount` counts all comments belonging to the post, including replies if the existing domain model supports nested comments. Use the same comment visibility/deletion rules as the existing comment listing API.
- A comment must belong to the specified post.
- Missing posts, missing comments, and mismatched post/comment IDs must follow existing error conventions.
- Do not load complete like/comment collections to calculate counts.

### Backend implementation

- Add count and existence queries to `LikeRepository` and `CommentRepository`.
- Use efficient database-level count/existence queries.
- Add small read-only service methods and typed response DTOs.
- Reuse existing entity relationships and repository conventions.
- Avoid introducing unnecessary abstractions or changing existing mutation services.

### Gateway implementation and security

- Permit the two summary GET routes through the gateway.
- Reuse the existing JWT validation and trusted identity propagation mechanism.
- For these routes only, forward an optional user ID derived from a validated JWT.
- Anonymous requests must continue to work without a JWT.
- Continue stripping caller-supplied identity headers.
- Never accept a viewer ID from query parameters, request bodies, or untrusted headers.
- Do not change identity handling on unrelated public routes.
- Do not weaken authentication or authorization for existing endpoints.

### Tests

Add focused repository, service/controller, and gateway tests for:
- Zero and nonzero counts.
- Anonymous requests.
- Authenticated requests with and without an existing like.
- Missing posts.
- Missing comments.
- Comments belonging to another post.
- Spoofed identity headers.
- Anonymous requests with forged identity headers.
- Optional authentication and valid JWT identity forwarding.

## 3. Improving email validation guidance

Update:
- `RegisterPage.tsx`
- `AccountSettingsPage.tsx`
- Create/edit forms in `AdminDashboardPage.tsx`

### Requirements

Use consistent invalid-format wording:

`Enter a valid email address, for example name@example.com.`

- Add the same example as accessible email-field guidance.
- Ensure the example is available even when a generic server-side format error is displayed.
- Keep required, length, duplicate-email, and other server errors distinct.
- Do not change email acceptance rules or user-service validation/business logic.
- Reuse shared frontend guidance only where it genuinely reduces duplication.
- Ensure guidance is associated with the email input using existing accessibility conventions.

## 4. Adding visible reaction icons and accurate counts

### Frontend API

- Add typed engagement response interfaces.
- Add summary API functions in `src/api/`.
- Use the existing centralized Axios client and gateway base URL.
- Do not call microservices directly.
- Follow existing API error-handling conventions.

### Components

Update:
- `LikeActions.tsx`
- `PostCard.tsx`
- `CommentItem.tsx`

Use existing Lucide icons:
- `ThumbsUp`
- `ThumbsDown`
- `MessageCircle`

### Like/Unlike behavior

The UI must reflect actual server state.

- ThumbsUp means Like.
- ThumbsDown means Unlike/remove an existing like.
- Never represent ThumbsDown as a separate persisted reaction.
- Never display a dislike count.

For authenticated users:
- If `likedByMe === false`, ThumbsUp is available and ThumbsDown is unavailable.
- If `likedByMe === true`, ThumbsDown is available and ThumbsUp is unavailable.
- Highlight ThumbsUp only when the confirmed server state is `likedByMe === true`.
- Do not show a selected/active state for ThumbsDown.
- Use accessible names and `aria-pressed` where semantically appropriate. Do not communicate a false dislike state.

For anonymous users:
- Display real counts.
- Both reaction mutations are unavailable.
- Do not expose misleading interactive controls.

### Loading, mutation, and error states

- Wait for confirmed server state before highlighting a reaction.
- Disable reaction actions during initial summary loading.
- Disable both reaction controls while a mutation is pending.
- Do not assume a mutation succeeded before the server confirms it.
- Refresh the engagement summary after successful Like/Unlike mutations.
- Also refresh after duplicate-like and missing-like responses, because server state may differ from the local assumption.
- Clear viewer-specific state on logout and account changes.
- Discard stale requests when the resource or authenticated viewer changes.
- Do not fabricate zero counts during loading or after a failed request.
- Provide explicit retryable feedback when engagement loading fails.
- A failed engagement request must not break the main post/comment rendering.
- While the engagement state is unknown or unavailable, do not permit Like/Unlike mutations.

### Counts and comments navigation

- Display actual like counts on posts and comments.
- Display actual comment counts on every post.
- Counts must come from engagement summary endpoints.
- Anonymous readers must be able to see totals.
- The post comments icon/count must link to the post detail comments section.
- Add a stable section anchor to the post detail comments section.
- Preserve keyboard accessibility and mobile layout.

### Comment mutation synchronization

- Refresh the parent post's comment total after comment creation and deletion.
- Do not remount the composer or lose its draft while refreshing the total.
- Preserve existing comment creation/deletion behavior.
- Refresh comment engagement after relevant mutations without introducing unnecessary full-page reloads.

## 5. Dismissing reaction notices smoothly within four seconds

Apply timed dismissal only to:
- Reaction success notices.
- Benign warnings, such as already-liked or no-existing-like feedback.

### Timing

- Total lifetime: 4,000 ms from creation.
- Visible without fading for approximately 3,600 ms.
- Fade during the final 400 ms.
- Remove at 4,000 ms.

### Replacement and cleanup

- Every new notice replaces the previous notice.
- A replacement starts a fresh four-second lifetime.
- An older timer must never remove a newer notice.
- Clean up timers on replacement and component unmount.
- Use the existing notification/alert mechanisms where possible.
- Do not change Alert behavior application-wide.

### Reduced motion

- Respect `prefers-reduced-motion`.
- When reduced motion is enabled, remove the notice without animation at the same four-second deadline.

### Genuine errors

Do not automatically dismiss:
- Network errors.
- Authorization errors.
- Missing-resource errors.
- Server errors.

These must remain visible and provide existing recovery actions where available.

## 6. Fixing English public-profile dates

Update `UserProfilePage.tsx`.

Apply an explicit `en-US` locale to:
- Member since.
- Last login.
- Profile updated.

Use the existing date values and timezone interpretation.

Expected example:

`Nov 23, 2026`

Requirements:
- Keep the existing no-login placeholder.
- Do not change date values or backend behavior.
- Do not change unrelated post/comment timestamp formatting.
- Verify that output remains English when the browser locale is non-English.

## 7. Enforcing five-item pagination

### Posts

Update `PostsPage.tsx`:
- Change page size from 10 to 5.
- Preserve existing Previous/Next navigation.
- Continue using Spring Page metadata.
- Replace unbounded local prepend after post creation with a page-zero server reload.
- Creating a post must never leave six visible posts or stale totals.

### Comments

Update `CommentList.tsx`:
- Change page size from 10 to 5.
- Preserve existing Previous/Next navigation and Spring Page metadata.
- Continue refreshing the current comment page after creation/deletion.
- Preserve stable ordering.
- Preserve fallback to the last valid page after deleting its final item.

### Pagination verification

Test:
- Empty lists.
- Exactly five items.
- Six or more items.
- First and last pages.
- Previous/Next disabled states.
- Correct total counts and page metadata.
- Creating an item on the first page.
- Deleting the final item on a page.
- Page fallback after deletion.

Do not change pagination for user directories or leaderboards.

## 8. Updating tests and documentation

### Frontend tests

Update affected existing tests, including:
- `authPages.test.tsx`
- `phase10.test.tsx`
- `phase11.test.tsx`
- Existing post/comment phase and regression tests.
- Profile tests.
- Gateway-backed frontend integration adapters.

Add focused tests for:

**Engagement**
- Accurate post/comment counts.
- Like state after initial load and reload.
- Like and Unlike mutations.
- Duplicate-like and missing-like responses.
- Anonymous readers.
- Logout and account switching.
- Comment creation/deletion and parent post count refresh.
- Summary loading failures and retry behavior.
- Stale request protection.

**Reaction notices**
- Removal at exactly 4,000 ms.
- Fade beginning at approximately 3,600 ms.
- Replacement resets the deadline.
- Old timers cannot remove newer notices.
- Unmount cleanup.
- Reduced-motion behavior.

**Email**
- Invalid-format guidance.
- Accessible field example.
- Distinct required, length, duplicate, and server errors.

**Dates and pagination**
- English dates under a non-English browser locale.
- Five-item post and comment pages.
- Navigation boundaries and creation/deletion behavior.

### Backend and gateway tests

Run targeted Post Service and Gateway Maven tests using existing Maven wrappers/reactor conventions.

Verify that existing Like transaction/service regression tests still pass and leaderboard scoring remains unchanged.

### Documentation

Update `frontend/README.md` with:
- Engagement summary API contracts.
- Anonymous and authenticated response behavior.
- Manual checks for five-item pages.
- English public-profile dates.
- Reaction icons and counts.
- Four-second notice behavior.

Reconcile frontend contributor instructions that describe the previous no-count/no-liked-state limitation with the explicitly approved read-only API extension. Retain the prohibition on unrelated backend and business-logic changes.

## 9. Implementation order

Implement in small, independently verifiable stages:

1. Inspect existing repository, gateway identity propagation, security, DTOs, error conventions, frontend API architecture, and current dirty worktree.
2. Implement Post Service engagement repository queries, DTOs, service methods, endpoints, and targeted tests.
3. Implement Gateway routing, optional validated-JWT identity forwarding, spoofing protection, and targeted tests.
4. Implement frontend typed summary APIs and reaction UI with loading, error, mutation, and viewer-change handling.
5. Implement email validation guidance.
6. Implement reaction notice timing and reduced-motion behavior.
7. Implement English public-profile date formatting.
8. Implement five-item post/comment pagination and mutation synchronization.
9. Update tests and README/contributor instructions.
10. Run the relevant frontend and backend regression suites.

Do not begin unrelated refactoring. Do not install new packages unless validation proves an existing dependency is missing.

## 10. Validation commands

From `frontend`:
- Run the smallest relevant Vitest test selection first.
- Run `npm run lint`.
- Run `npm run build`.

For backend:
- Run targeted Post Service Maven tests.
- Run targeted Gateway Maven tests.
- Run existing Like transaction/service regression tests.
- Confirm leaderboard scoring behavior is unchanged.

Perform manual verification through the Gateway:
- Anonymous users can see engagement totals.
- Authenticated users see their actual Like state after reload.
- Like/Unlike updates counts and state correctly.
- Spoofed identity headers cannot change viewer identity.
- Post/comment creation and deletion refresh the correct counts.
- Keyboard navigation and accessible labels work.
- Mobile layout remains usable.
- Profile dates remain English under a non-English browser locale.
- Reaction notices fade and disappear within four seconds.
- Genuine errors remain visible and recoverable.
- Posts and comments never exceed five items per page.

## 11. Acceptance criteria

- Invalid email feedback includes a concrete valid example without obscuring distinct errors.
- Reaction success and benign warning notices disappear within four seconds.
- Genuine errors remain visible and recoverable.
- Authenticated users see accurate confirmed Like/Unlike state after reload.
- ThumbsDown is only an Unlike action when a like exists.
- Anonymous users can see accurate counts but cannot mutate reactions.
- Every post displays actual like and comment totals.
- Every comment displays its actual like total.
- The comments icon/count links to the post detail comments section.
- Public-profile dates are English regardless of browser locale.
- Neither posts nor comments display more than five items per page, including after creation/deletion.
- Existing Like/Unlike, authorization, self-like, deletion, and leaderboard business behavior remains unchanged.
- Existing unrelated worktree changes are preserved.