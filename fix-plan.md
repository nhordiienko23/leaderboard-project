# Profile Activity Counts and Responsive Content

## Problem and approach

Show activity counts below personal information on every user's profile, and keep long post and comment content inside the viewport without truncating it.

Profile information comes from User Service. Posts, comments, and likes are owned by Post Service, which is the source of truth for activity metrics.

Expose a dedicated read-only aggregate endpoint in Post Service. The frontend must consume its response directly rather than counting paginated results or using leaderboard scores.

Preserve existing authorization, API conventions, business rules, and user-owned repository changes.

## Todos

### 1. Add profile activity aggregation in post-service

Add a typed response DTO and a read-only service/controller endpoint under the existing `/api/posts/**` Gateway route, keyed by user ID.

Suggested endpoint:

`GET /api/posts/users/{userId}/activity`

Suggested response:

```json
{
  "postsCreated": 12,
  "commentsAuthored": 35,
  "postLikesGiven": 20,
  "commentLikesGiven": 14,
  "postLikesReceived": 48,
  "commentLikesReceived": 9
}
```

All six fields must always be returned as non-negative numbers, including zero.

Metrics:

- `postsCreated`: existing posts authored by the user.
- `commentsAuthored`: existing comments authored by the user.
- `postLikesGiven`: existing likes created by the user targeting posts.
- `commentLikesGiven`: existing likes created by the user targeting comments.
- `postLikesReceived`: existing likes targeting posts authored by the user.
- `commentLikesReceived`: existing likes targeting comments authored by the user.

Implementation requirements:

- Inspect the existing entity relationships, repository methods, like model, deletion behavior, and authorization before implementing.
- Use database-level count queries. Do not load entities or fetch paginated results to calculate counts.
- Keep post and comment like targets separate. One like record must contribute to exactly one target category.
- Count existing like records, including self-likes if the current business rules allow them.
- Respect existing uniqueness constraints and like creation/deletion behavior.
- Received likes must be attributed to the current author of the target post or comment.
- Respect existing deletion and cascade behavior. Deleted content and deleted associated likes must not contribute to counts.
- Avoid a complicated multi-join query that multiplies counts. Prefer simple, independently verifiable aggregate queries where appropriate.
- Validate user IDs consistently with existing API conventions.
- Return the established not-found or validation response for invalid IDs according to current project conventions.
- Keep the endpoint read-only and use the existing transaction and service patterns.
- Do not introduce new dependencies, tables, event consumers, caches, or schema changes unless inspection proves they are strictly necessary.

Authorization:

- Reuse existing authenticated profile access rules.
- Do not introduce anonymous access.
- Ensure Gateway routing and security permit the intended authenticated viewers and reject unauthorized requests.
- Do not duplicate or weaken existing security configuration.

### 2. Integrate counts into the profile

Add a typed frontend API method for the new endpoint.

Fetch activity counts for the currently viewed user's ID.

Display nonzero metrics below personal information and before subsequent profile content.

Use clear labels:

- Posts
- Comments
- Post likes given
- Comment likes given
- Post likes received
- Comment likes received

Requirements:

- Hide the entire activity section when all six values are zero.
- Show all nonzero values with readable labels.
- Do not display negative, invalid, or malformed values.
- Keep profile information visible while activity is loading or if activity loading fails.
- Use a separate loading state for activity.
- Use a separate error state with a Retry action.
- Do not treat failed requests as zero counts.
- Prevent stale responses from updating the currently viewed profile.
- When navigating between users, never display the previous user's counts for the new profile.
- Clean up or invalidate pending request results when the viewed user changes or the component unmounts.
- Preserve existing profile authorization, loading, error, and navigation behavior.
- Do not change unrelated profile functionality or user summary caching.

### 3. Fix responsive post and comment layouts

Update shared `PostCard` and `CommentItem` containers and text wrapping.

Goal: preserve the complete content while preventing long text from expanding the page beyond the viewport.

Requirements:

- Preserve all post and comment text. Do not truncate, clip, hide, or replace content.
- Preserve intentional whitespace and line breaks where the existing content model supports them.
- Break long unbroken strings, URLs, and other text that would otherwise overflow.
- Use appropriate `overflow-wrap`, `white-space`, `min-width`, and flex/grid shrink behavior.
- Ensure nested flex/grid children can shrink using `min-w-0` where necessary.
- Allow action buttons and controls to shrink or wrap on narrow screens.
- Keep Like, Edit, Delete, profile links, and other existing actions accessible and functional.
- Preserve owner and non-owner behavior.
- Preserve existing feed and detail page behavior.
- Do not solve the problem by hiding page-level overflow or adding broad horizontal clipping.
- Avoid unnecessary changes to unrelated page layouts and shared components.

### 4. Verify and document

#### Backend tests

Add or update Post Service repository, service, and controller tests covering:

- All six activity metrics.
- Posts and comments with zero activity.
- Post likes given and received.
- Comment likes given and received.
- Both possible like targets without double counting.
- Self-likes when allowed by existing rules.
- Deletion of posts and comments and the resulting counts.
- Deletion or removal of likes.
- Invalid and nonexistent user IDs.
- Read-only behavior.
- Authorization and Gateway routing where applicable.

Use the existing test stack and test conventions.

#### Frontend tests

Update profile tests that currently assert that activity counts are absent.

Cover:

- Nonzero activity counts.
- All-zero counts and hidden section.
- Activity loading.
- Activity request failure.
- Retry and recovery.
- Profile information remaining visible during activity failures.
- Navigation between users.
- Stale response protection.
- Invalid response values.
- Correct labels and placement below personal information.

For responsive content, cover both posts and comments:

- Long unbroken strings.
- URLs.
- Preserved whitespace and line breaks.
- Owner and non-owner views.
- Action controls remaining present and usable.
- Feed and detail views.

#### Real browser verification

Use existing browser tooling if available.

Test at:

- 320px
- 375px
- 768px
- 1280px

Include long unbroken words, URLs, and text with whitespace.

Verify:

- Document width does not exceed viewport width.
- Post and comment containers do not exceed viewport width.
- Text remains fully visible and wraps correctly.
- Action controls remain accessible and do not cause horizontal overflow.
- Both feed and detail views behave correctly.

Do not treat jsdom layout assertions as proof of responsive browser behavior.

If browser tooling is unavailable, report this limitation explicitly. Do not add a new browser dependency solely for this task.

#### Documentation and final verification

Update only directly relevant API documentation.

Run:

- Focused Post Service tests.
- Relevant Gateway security/routing tests.
- Focused frontend tests.
- Full frontend test suite.
- TypeScript check.
- Production build.
- Relevant full Post Service tests if practical.

Report exact commands and actual results.

## Considerations and constraints

- Metrics reflect currently stored data, not historical activity.
- Deleting a post or comment and its associated likes changes the totals according to existing deletion rules.
- The new counts are available only to viewers who already have authenticated profile access.
- No anonymous-profile access is added.
- Profile and activity requests have independent failure states.
- Existing text length limits remain unchanged.
- This is a display fix, not a change to allowed content size.
- Do not count activity using paginated frontend endpoints.
- Do not use leaderboard scores as a substitute for activity.
- Do not change unrelated post/comment author-name handling.
- Do not modify ranking, scoring, or leaderboard behavior.
- No new dependencies unless strictly necessary and explicitly justified.
- Preserve all existing staged, modified, and untracked user changes.
- Do not run destructive Git commands.
- Do not reset Docker/database volumes.
- Do not stage or commit files.

## Completion criteria

All four todos are implemented and verified to the extent supported by the available environment.

The final report must distinguish automated verification from actual runtime/browser verification.

Do not claim browser responsiveness is verified if only jsdom tests were run.