package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.service.LikeService;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String POSTS_URL = "/api/posts";

    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_AUTHOR_ID = 31L;
    private static final Long OTHER_USER_ID = 42L;
    private static final Long ADMIN_ID = 99L;
    private static final Long COMMENT_LIKER_ID = 51L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private LikeService likeService;

    @BeforeEach
    void cleanDatabase() {
        likeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Test
    void createComment_shouldPersistCommentAndCreateOutboxEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post for comments");

        mockMvc.perform(post(commentsUrl(postId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Integration test comment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.content")
                        .value("Integration test comment"))
                .andExpect(jsonPath("$.userId").value(COMMENT_AUTHOR_ID))
                .andExpect(jsonPath("$.postId").value(postId));

        Comment comment = findComment(
                postId,
                COMMENT_AUTHOR_ID,
                "Integration test comment"
        );

        OutboxEntry event = findOutboxEvent(
                comment.getId(),
                EventType.COMMENT,
                ActionType.CREATED
        );

        assertEquals(COMMENT_AUTHOR_ID, event.getActorId());
        assertEquals(COMMENT_AUTHOR_ID, event.getContentAuthorId());
        assertEquals(POST_AUTHOR_ID, event.getTargetAuthorId());
        assertEquals(EventReason.USER_ACTION, event.getReason());
    }

    @Test
    void createComment_selfCommentShouldNotCreateScoringEvent()
            throws Exception {

        Long postId = createPostThroughApi("My post");

        mockMvc.perform(post(commentsUrl(postId))
                        .header("X-User-Id", POST_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Comment on my own post"
                                }
                                """))
                .andExpect(status().isCreated());

        assertEquals(1, commentRepository.findAllByPost_Id(postId).size());

        assertEquals(
                0L,
                countOutboxEvents(EventType.COMMENT, ActionType.CREATED)
        );
    }

    @Test
    void createComment_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(post(commentsUrl(999999L))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Comment on missing post"}
                                """))
                .andExpect(status().isNotFound());

        assertEquals(0L, commentRepository.count());

        assertEquals(
                0L,
                countOutboxEvents(EventType.COMMENT, ActionType.CREATED)
        );
    }

    @Test
    void createComment_shouldRejectBlankContent() throws Exception {
        Long postId = createPostThroughApi("Post for validation");

        mockMvc.perform(post(commentsUrl(postId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   "}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0L, commentRepository.count());

        assertEquals(
                0L,
                countOutboxEvents(EventType.COMMENT, ActionType.CREATED)
        );
    }

    @Test
    void createComment_shouldRejectContentLongerThan1000Characters()
            throws Exception {

        Long postId = createPostThroughApi("Post for validation");
        String content = "x".repeat(1001);

        mockMvc.perform(post(commentsUrl(postId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isBadRequest());

        assertEquals(0L, commentRepository.count());

        assertEquals(
                0L,
                countOutboxEvents(EventType.COMMENT, ActionType.CREATED)
        );
    }

    @Test
    void createComment_shouldAcceptContentOfExactly1000Characters()
            throws Exception {

        Long postId = createPostThroughApi("Post for boundary test");
        String content = "x".repeat(1000);

        mockMvc.perform(post(commentsUrl(postId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(content));

        Comment savedComment = findComment(
                postId,
                COMMENT_AUTHOR_ID,
                content
        );

        assertEquals(1000, savedComment.getContent().length());
    }

    @Test
    void createComment_shouldRejectMissingUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post without user header");

        mockMvc.perform(post(commentsUrl(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Comment without user ID"}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0L, commentRepository.count());
    }

    @Test
    void createComment_shouldRejectInvalidUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post with invalid header");

        mockMvc.perform(post(commentsUrl(postId))
                        .header("X-User-Id", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Invalid user ID"}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0L, commentRepository.count());
    }

    @Test
    void createComment_shouldRejectMalformedJson() throws Exception {
        Long postId = createPostThroughApi("Post with malformed JSON");

        mockMvc.perform(post(commentsUrl(postId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0L, commentRepository.count());
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Test
    void getComments_shouldReturnEmptyPageWhenNoCommentsExist()
            throws Exception {

        Long postId = createPostThroughApi("Post without comments");

        mockMvc.perform(get(commentsUrl(postId))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    @Test
    void getComments_shouldReturnPaginatedComments()
            throws Exception {

        Long postId = createPostThroughApi("Post with comments");

        createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "First comment"
        );
        createCommentThroughApi(
                postId, OTHER_USER_ID, "Second comment"
        );

        mockMvc.perform(get(commentsUrl(postId))
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content")
                        .value("First comment"))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        mockMvc.perform(get(commentsUrl(postId))
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content")
                        .value("Second comment"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void getComments_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(get(commentsUrl(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComment_shouldReturnComment() throws Exception {
        Long postId = createPostThroughApi("Post for reading");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to read"
        );

        mockMvc.perform(get(commentUrl(postId, commentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId))
                .andExpect(jsonPath("$.content").value("Comment to read"))
                .andExpect(jsonPath("$.userId").value(COMMENT_AUTHOR_ID))
                .andExpect(jsonPath("$.postId").value(postId));
    }

    @Test
    void getComment_shouldReturn404WhenCommentDoesNotExist()
            throws Exception {

        Long postId = createPostThroughApi("Post without this comment");

        mockMvc.perform(get(commentUrl(postId, 999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComment_shouldReturn404WhenCommentBelongsToAnotherPost()
            throws Exception {

        Long firstPostId = createPostThroughApi("First post");
        Long secondPostId = createPostThroughApi("Second post");

        Long commentId = createCommentThroughApi(
                firstPostId, COMMENT_AUTHOR_ID, "Comment on first post"
        );

        mockMvc.perform(get(commentUrl(secondPostId, commentId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComment_shouldRejectNonNumericIds() throws Exception {
        mockMvc.perform(get("/api/posts/not-a-number/comments/1"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Test
    void updateComment_ownerShouldUpdateContent() throws Exception {
        Long postId = createPostThroughApi("Post for update");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Original comment"
        );

        long outboxCountBefore = outboxRepository.count();

        mockMvc.perform(put(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Updated comment"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId))
                .andExpect(jsonPath("$.content").value("Updated comment"))
                .andExpect(jsonPath("$.userId").value(COMMENT_AUTHOR_ID))
                .andExpect(jsonPath("$.postId").value(postId));

        assertEquals(
                "Updated comment",
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getContent()
        );

        // Updating comment text does not create scoring events.
        assertEquals(outboxCountBefore, outboxRepository.count());
    }

    @Test
    void updateComment_adminShouldUpdateAnotherUsersComment()
            throws Exception {

        Long postId = createPostThroughApi("Post for admin update");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Original comment"
        );

        mockMvc.perform(put(commentUrl(postId, commentId))
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Updated by admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated by admin"));

        assertEquals(
                "Updated by admin",
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getContent()
        );
    }

    @Test
    void updateComment_shouldRejectNonOwner() throws Exception {
        Long postId = createPostThroughApi("Protected post");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Protected comment"
        );

        long outboxCountBefore = outboxRepository.count();

        mockMvc.perform(put(commentUrl(postId, commentId))
                        .header("X-User-Id", OTHER_USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Unauthorized update"}
                                """))
                .andExpect(status().isForbidden());

        assertEquals(
                "Protected comment",
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getContent()
        );

        assertEquals(outboxCountBefore, outboxRepository.count());
    }

    @Test
    void updateComment_shouldReturn404WhenCommentDoesNotExist()
            throws Exception {

        Long postId = createPostThroughApi("Post for missing comment");

        mockMvc.perform(put(commentUrl(postId, 999999L))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Updated comment"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateComment_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(put(commentUrl(999999L, 1L))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Updated comment"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateComment_shouldReturn404WhenCommentBelongsToAnotherPost()
            throws Exception {

        Long firstPostId = createPostThroughApi("First post");
        Long secondPostId = createPostThroughApi("Second post");

        Long commentId = createCommentThroughApi(
                firstPostId, COMMENT_AUTHOR_ID, "Original comment"
        );

        mockMvc.perform(put(commentUrl(secondPostId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Unauthorized update"}
                                """))
                .andExpect(status().isNotFound());

        assertEquals(
                "Original comment",
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getContent()
        );
    }

    @Test
    void updateComment_shouldRejectBlankContent() throws Exception {
        Long postId = createPostThroughApi("Post for validation");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Original comment"
        );

        mockMvc.perform(put(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":" "}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(
                "Original comment",
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getContent()
        );
    }

    @Test
    void updateComment_shouldRejectContentLongerThan1000Characters()
            throws Exception {

        Long postId = createPostThroughApi("Post for validation");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Original comment"
        );

        String content = "x".repeat(1001);

        mockMvc.perform(put(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isBadRequest());

        assertEquals(
                "Original comment",
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getContent()
        );
    }

    @Test
    void updateComment_shouldRejectMissingUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post for missing header");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Original comment"
        );

        mockMvc.perform(put(commentUrl(postId, commentId))
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Updated comment"}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(
                "Original comment",
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getContent()
        );
    }

    @Test
    void updateComment_shouldRejectMissingRoleHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post for missing role");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Original comment"
        );

        mockMvc.perform(put(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Updated comment"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateComment_shouldRejectUnknownRole() throws Exception {
        Long postId = createPostThroughApi("Post for invalid role");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Original comment"
        );

        mockMvc.perform(put(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Updated comment"}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(
                "Original comment",
                commentRepository.findById(commentId)
                        .orElseThrow()
                        .getContent()
        );
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Test
    void deleteComment_ownerShouldDeleteCommentAndCreateDeletionEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post for comment deletion");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to delete"
        );

        mockMvc.perform(delete(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNoContent());

        assertTrue(commentRepository.findById(commentId).isEmpty());

        OutboxEntry deletionEvent = findOutboxEvent(
                commentId,
                EventType.COMMENT,
                ActionType.DELETED
        );

        assertEquals(EventReason.USER_ACTION, deletionEvent.getReason());
        assertEquals(COMMENT_AUTHOR_ID, deletionEvent.getActorId());
        assertEquals(COMMENT_AUTHOR_ID, deletionEvent.getContentAuthorId());
        assertEquals(POST_AUTHOR_ID, deletionEvent.getTargetAuthorId());
    }

    @Test
    void deleteComment_shouldDeleteCommentLikesAndReverseTheirScores()
            throws Exception {

        Long postId = createPostThroughApi("Post with a liked comment");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment with a like"
        );

        likeService.likeComment(COMMENT_LIKER_ID, postId, commentId);

        Long likeId = likeRepository
                .findByUserIdAndComment_Id(COMMENT_LIKER_ID, commentId)
                .orElseThrow()
                .getId();

        assertEquals(1, likeRepository.findAllByComment_Id(commentId).size());

        mockMvc.perform(delete(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNoContent());

        assertTrue(commentRepository.findById(commentId).isEmpty());
        assertTrue(likeRepository.findById(likeId).isEmpty());

        OutboxEntry commentDeletion = findOutboxEvent(
                commentId,
                EventType.COMMENT,
                ActionType.DELETED
        );

        OutboxEntry likeDeletion = findOutboxEvent(
                likeId,
                EventType.LIKE,
                ActionType.DELETED
        );

        assertEquals(EventReason.USER_ACTION, commentDeletion.getReason());
        assertEquals(EventReason.USER_ACTION, likeDeletion.getReason());
        assertEquals(COMMENT_AUTHOR_ID, commentDeletion.getActorId());
        assertEquals(COMMENT_AUTHOR_ID, likeDeletion.getActorId());
    }

    @Test
    void deleteComment_adminShouldDeleteAnotherUsersComment()
            throws Exception {

        Long postId = createPostThroughApi("Post for moderation");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment for moderation"
        );

        mockMvc.perform(delete(commentUrl(postId, commentId))
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());

        assertTrue(commentRepository.findById(commentId).isEmpty());

        OutboxEntry deletionEvent = findOutboxEvent(
                commentId,
                EventType.COMMENT,
                ActionType.DELETED
        );

        assertEquals(EventReason.MODERATION, deletionEvent.getReason());
        assertEquals(ADMIN_ID, deletionEvent.getActorId());
    }

    @Test
    void deleteComment_adminShouldMarkCommentAndLikeEventsAsModeration()
            throws Exception {

        Long postId = createPostThroughApi("Post for moderation with a like");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment with a like"
        );

        likeService.likeComment(COMMENT_LIKER_ID, postId, commentId);

        Long likeId = likeRepository
                .findByUserIdAndComment_Id(COMMENT_LIKER_ID, commentId)
                .orElseThrow()
                .getId();

        mockMvc.perform(delete(commentUrl(postId, commentId))
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());

        assertTrue(commentRepository.findById(commentId).isEmpty());
        assertTrue(likeRepository.findById(likeId).isEmpty());

        OutboxEntry commentDeletion = findOutboxEvent(
                commentId,
                EventType.COMMENT,
                ActionType.DELETED
        );

        OutboxEntry likeDeletion = findOutboxEvent(
                likeId,
                EventType.LIKE,
                ActionType.DELETED
        );

        assertEquals(EventReason.MODERATION, commentDeletion.getReason());
        assertEquals(EventReason.MODERATION, likeDeletion.getReason());
        assertEquals(ADMIN_ID, commentDeletion.getActorId());
        assertEquals(ADMIN_ID, likeDeletion.getActorId());
    }

    @Test
    void deleteComment_selfCommentShouldNotCreateScoreReversal()
            throws Exception {

        Long postId = createPostThroughApi("Post for self-comment");

        Long commentId = createCommentThroughApi(
                postId, POST_AUTHOR_ID, "Self-comment"
        );

        assertEquals(
                0L,
                countOutboxEvents(EventType.COMMENT, ActionType.CREATED)
        );

        mockMvc.perform(delete(commentUrl(postId, commentId))
                        .header("X-User-Id", POST_AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNoContent());

        assertTrue(commentRepository.findById(commentId).isEmpty());

        assertEquals(
                0L,
                countOutboxEvents(EventType.COMMENT, ActionType.DELETED)
        );
    }

    @Test
    void deleteComment_shouldRejectNonOwner() throws Exception {
        Long postId = createPostThroughApi("Post with protected comment");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Protected comment"
        );

        mockMvc.perform(delete(commentUrl(postId, commentId))
                        .header("X-User-Id", OTHER_USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        assertTrue(commentRepository.findById(commentId).isPresent());

        assertEquals(
                0L,
                countOutboxEvents(EventType.COMMENT, ActionType.DELETED)
        );
    }

    @Test
    void deleteComment_shouldReturn404WhenCommentDoesNotExist()
            throws Exception {

        Long postId = createPostThroughApi("Post without target comment");

        mockMvc.perform(delete(commentUrl(postId, 999999L))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteComment_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(delete(commentUrl(999999L, 1L))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteComment_shouldReturn404WhenCommentBelongsToAnotherPost()
            throws Exception {

        Long firstPostId = createPostThroughApi("First post");
        Long secondPostId = createPostThroughApi("Second post");

        Long commentId = createCommentThroughApi(
                firstPostId, COMMENT_AUTHOR_ID, "Original comment"
        );

        mockMvc.perform(delete(commentUrl(secondPostId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());

        assertTrue(commentRepository.findById(commentId).isPresent());

        assertEquals(
                0L,
                countOutboxEvents(EventType.COMMENT, ActionType.DELETED)
        );
    }

    @Test
    void deleteComment_shouldRejectMissingRoleHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post for missing role");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to preserve"
        );

        mockMvc.perform(delete(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID))
                .andExpect(status().isBadRequest());

        assertTrue(commentRepository.findById(commentId).isPresent());
    }

    @Test
    void deleteComment_shouldRejectUnknownRole() throws Exception {
        Long postId = createPostThroughApi("Post for invalid role");

        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to preserve"
        );

        mockMvc.perform(delete(commentUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID)
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isBadRequest());

        assertTrue(commentRepository.findById(commentId).isPresent());
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private String commentsUrl(Long postId) {
        return POSTS_URL + "/" + postId + "/comments";
    }

    private String commentUrl(Long postId, Long commentId) {
        return commentsUrl(postId) + "/" + commentId;
    }

    private Long createPostThroughApi(String content) throws Exception {
        mockMvc.perform(post(POSTS_URL)
                        .header("X-User-Id", POST_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isCreated());

        return postRepository.findAll().stream()
                .filter(post -> content.equals(post.getContent()))
                .map(com.github.nhordiienko23.postservice.entity.Post::getId)
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Created post was not persisted")
                );
    }

    private Long createCommentThroughApi(
            Long postId,
            Long userId,
            String content
    ) throws Exception {

        mockMvc.perform(post(commentsUrl(postId))
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isCreated());

        return findComment(postId, userId, content).getId();
    }

    private Comment findComment(
            Long postId,
            Long userId,
            String content
    ) {
        return commentRepository.findAllByPost_Id(postId).stream()
                .filter(comment ->
                        userId.equals(comment.getUserId())
                                && content.equals(comment.getContent())
                )
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Created comment was not persisted")
                );
    }

    private OutboxEntry findOutboxEvent(
            Long contentId,
            EventType eventType,
            ActionType actionType
    ) {
        return outboxRepository.findAll().stream()
                .filter(event ->
                        contentId.equals(event.getContentId())
                                && eventType == event.getEventType()
                                && actionType == event.getActionType()
                )
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Outbox event not found: contentId=" + contentId
                                        + ", eventType=" + eventType
                                        + ", actionType=" + actionType
                        )
                );
    }

    private long countOutboxEvents(
            EventType eventType,
            ActionType actionType
    ) {
        return outboxRepository.findAll().stream()
                .filter(event ->
                        eventType == event.getEventType()
                                && actionType == event.getActionType()
                )
                .count();
    }
}