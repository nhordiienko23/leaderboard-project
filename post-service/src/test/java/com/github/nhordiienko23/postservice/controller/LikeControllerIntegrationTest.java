package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Like;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class LikeControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String POSTS_URL = "/api/posts";

    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_AUTHOR_ID = 31L;
    private static final Long LIKER_ID = 41L;
    private static final Long OTHER_LIKER_ID = 42L;

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

    @BeforeEach
    void cleanDatabase() {
        likeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // LIKE POST
    // -------------------------------------------------------------------------

    @Test
    void likePost_shouldPersistLikeAndCreateOutboxEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post to like");

        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(LIKER_ID))
                .andExpect(jsonPath("$.postId").value(postId));

        Like like = likeRepository
                .findByUserIdAndPost_Id(LIKER_ID, postId)
                .orElseThrow();

        OutboxEntry event = findOutboxEvent(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        );

        assertEquals(LIKER_ID, event.getActorId());
        assertEquals(POST_AUTHOR_ID, event.getContentAuthorId());
        assertEquals(POST_AUTHOR_ID, event.getTargetAuthorId());
        assertEquals(EventReason.USER_ACTION, event.getReason());

        awaitOutboxPublished(event.getId());
    }

    @Test
    void likePost_shouldPersistSelfLikeWithoutScoringEvent()
            throws Exception {

        Long postId = createPostThroughApi("My own post");

        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", POST_AUTHOR_ID))
                .andExpect(status().isCreated());

        assertTrue(
                likeRepository.existsByUserIdAndPost_Id(
                        POST_AUTHOR_ID, postId
                )
        );

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.CREATED)
        );
    }

    @Test
    void likePost_shouldReturn409WhenUserAlreadyLikedPost()
            throws Exception {

        Long postId = createPostThroughApi("Post to like twice");

        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isConflict());

        assertEquals(
                1L,
                likeRepository.findAllByPost_Id(postId).size()
        );

        assertEquals(
                1L,
                countOutboxEvents(EventType.LIKE, ActionType.CREATED)
        );
    }

    @Test
    void likePost_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(post(postLikesUrl(999999L))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());

        assertEquals(0L, likeRepository.count());

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.CREATED)
        );
    }

    @Test
    void likePost_shouldRejectMissingUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post without user header");

        mockMvc.perform(post(postLikesUrl(postId)))
                .andExpect(status().isBadRequest());

        assertEquals(0L, likeRepository.count());
    }

    @Test
    void likePost_shouldRejectInvalidUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post with invalid header");

        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", "not-a-number"))
                .andExpect(status().isBadRequest());

        assertEquals(0L, likeRepository.count());
    }

    // -------------------------------------------------------------------------
    // UNLIKE POST
    // -------------------------------------------------------------------------

    @Test
    void unlikePost_shouldDeleteLikeAndCreateDeletionEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post to unlike");

        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isCreated());

        Like like = likeRepository
                .findByUserIdAndPost_Id(LIKER_ID, postId)
                .orElseThrow();

        mockMvc.perform(delete(postLikesUrl(postId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNoContent());

        assertTrue(
                likeRepository.findByUserIdAndPost_Id(LIKER_ID, postId)
                        .isEmpty()
        );

        OutboxEntry deletionEvent = findOutboxEvent(
                like.getId(),
                EventType.LIKE,
                ActionType.DELETED
        );

        assertEquals(EventReason.USER_ACTION, deletionEvent.getReason());
        assertEquals(LIKER_ID, deletionEvent.getActorId());
        assertEquals(POST_AUTHOR_ID, deletionEvent.getContentAuthorId());

        awaitOutboxPublished(deletionEvent.getId());
    }

    @Test
    void unlikePost_shouldDeleteSelfLikeWithoutScoringEvent()
            throws Exception {

        Long postId = createPostThroughApi("My post");

        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", POST_AUTHOR_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(postLikesUrl(postId))
                        .header("X-User-Id", POST_AUTHOR_ID))
                .andExpect(status().isNoContent());

        assertTrue(
                likeRepository.findByUserIdAndPost_Id(
                        POST_AUTHOR_ID, postId
                ).isEmpty()
        );

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.CREATED)
        );
        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.DELETED)
        );
    }

    @Test
    void unlikePost_shouldNotRemoveAnotherUsersLike()
            throws Exception {

        Long postId = createPostThroughApi("Post with another user's like");

        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(postLikesUrl(postId))
                        .header("X-User-Id", OTHER_LIKER_ID))
                .andExpect(status().isNotFound());

        assertTrue(
                likeRepository.existsByUserIdAndPost_Id(LIKER_ID, postId)
        );

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.DELETED)
        );
    }

    @Test
    void unlikePost_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(delete(postLikesUrl(999999L))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void unlikePost_shouldReturn404WhenLikeDoesNotExist()
            throws Exception {

        Long postId = createPostThroughApi("Post without a like");

        mockMvc.perform(delete(postLikesUrl(postId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());

        assertEquals(0L, likeRepository.count());

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.DELETED)
        );
    }

    @Test
    void unlikePost_shouldRejectMissingUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post for unlike header test");

        mockMvc.perform(delete(postLikesUrl(postId)))
                .andExpect(status().isBadRequest());

        assertEquals(0L, likeRepository.count());
    }

    // -------------------------------------------------------------------------
    // LIKE COMMENT
    // -------------------------------------------------------------------------

    @Test
    void likeComment_shouldPersistLikeAndCreateOutboxEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post with comment");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to like"
        );

        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(LIKER_ID))
                .andExpect(jsonPath("$.commentId").value(commentId));

        Like like = likeRepository
                .findByUserIdAndComment_Id(LIKER_ID, commentId)
                .orElseThrow();

        OutboxEntry event = findOutboxEvent(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        );

        assertEquals(LIKER_ID, event.getActorId());
        assertEquals(COMMENT_AUTHOR_ID, event.getContentAuthorId());
        assertEquals(COMMENT_AUTHOR_ID, event.getTargetAuthorId());
        assertEquals(EventReason.USER_ACTION, event.getReason());

        awaitOutboxPublished(event.getId());
    }

    @Test
    void likeComment_shouldPersistSelfLikeWithoutScoringEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post with own comment");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "My own comment"
        );

        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID))
                .andExpect(status().isCreated());

        assertTrue(
                likeRepository.existsByUserIdAndComment_Id(
                        COMMENT_AUTHOR_ID, commentId
                )
        );

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.CREATED)
        );
    }

    @Test
    void likeComment_shouldReturn409WhenUserAlreadyLikedComment()
            throws Exception {

        Long postId = createPostThroughApi("Post for duplicate like test");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to like twice"
        );

        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isConflict());

        assertEquals(1L, likeRepository.findAllByComment_Id(commentId).size());

        assertEquals(
                1L,
                countOutboxEvents(EventType.LIKE, ActionType.CREATED)
        );
    }

    @Test
    void likeComment_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(post(commentLikesUrl(999999L, 1L))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());

        assertEquals(0L, likeRepository.count());
    }

    @Test
    void likeComment_shouldReturn404WhenCommentDoesNotExist()
            throws Exception {

        Long postId = createPostThroughApi("Post without target comment");

        mockMvc.perform(post(commentLikesUrl(postId, 999999L))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());

        assertEquals(0L, likeRepository.count());
    }

    @Test
    void likeComment_shouldReturn404WhenCommentBelongsToAnotherPost()
            throws Exception {

        Long firstPostId = createPostThroughApi("First post");
        Long secondPostId = createPostThroughApi("Second post");

        Long commentId = createCommentThroughApi(
                firstPostId, COMMENT_AUTHOR_ID, "Comment on first post"
        );

        mockMvc.perform(post(commentLikesUrl(secondPostId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());

        assertEquals(0L, likeRepository.count());
    }

    @Test
    void likeComment_shouldRejectMissingUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post for missing header");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to like"
        );

        mockMvc.perform(post(commentLikesUrl(postId, commentId)))
                .andExpect(status().isBadRequest());

        assertEquals(0L, likeRepository.count());
    }

    @Test
    void likeComment_shouldRejectInvalidUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post for invalid header");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to like"
        );

        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", "invalid"))
                .andExpect(status().isBadRequest());

        assertEquals(0L, likeRepository.count());
    }

    // -------------------------------------------------------------------------
    // UNLIKE COMMENT
    // -------------------------------------------------------------------------

    @Test
    void unlikeComment_shouldDeleteLikeAndCreateDeletionEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post for comment unlike");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment with like"
        );

        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isCreated());

        Like like = likeRepository
                .findByUserIdAndComment_Id(LIKER_ID, commentId)
                .orElseThrow();

        mockMvc.perform(delete(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNoContent());

        assertTrue(
                likeRepository.findByUserIdAndComment_Id(LIKER_ID, commentId)
                        .isEmpty()
        );

        OutboxEntry deletionEvent = findOutboxEvent(
                like.getId(),
                EventType.LIKE,
                ActionType.DELETED
        );

        assertEquals(EventReason.USER_ACTION, deletionEvent.getReason());
        assertEquals(LIKER_ID, deletionEvent.getActorId());
        assertEquals(COMMENT_AUTHOR_ID, deletionEvent.getContentAuthorId());

        awaitOutboxPublished(deletionEvent.getId());
    }

    @Test
    void unlikeComment_shouldDeleteSelfLikeWithoutScoringEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post for own comment like");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "My comment"
        );

        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", COMMENT_AUTHOR_ID))
                .andExpect(status().isNoContent());

        assertTrue(
                likeRepository.findByUserIdAndComment_Id(
                        COMMENT_AUTHOR_ID, commentId
                ).isEmpty()
        );

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.CREATED)
        );
        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.DELETED)
        );
    }

    @Test
    void unlikeComment_shouldNotRemoveAnotherUsersLike()
            throws Exception {

        Long postId = createPostThroughApi("Post for another user's like");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment with another user's like"
        );

        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", OTHER_LIKER_ID))
                .andExpect(status().isNotFound());

        assertTrue(
                likeRepository.existsByUserIdAndComment_Id(
                        LIKER_ID, commentId
                )
        );

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.DELETED)
        );
    }

    @Test
    void unlikeComment_shouldReturn404WhenLikeDoesNotExist()
            throws Exception {

        Long postId = createPostThroughApi("Post without comment like");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment without like"
        );

        mockMvc.perform(delete(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());

        assertEquals(0L, likeRepository.count());
    }

    @Test
    void unlikeComment_shouldReturn404WhenCommentDoesNotExist()
            throws Exception {

        Long postId = createPostThroughApi("Post without target comment");

        mockMvc.perform(delete(commentLikesUrl(postId, 999999L))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void unlikeComment_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(delete(commentLikesUrl(999999L, 1L))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void unlikeComment_shouldReturn404WhenCommentBelongsToAnotherPost()
            throws Exception {

        Long firstPostId = createPostThroughApi("First post");
        Long secondPostId = createPostThroughApi("Second post");

        Long commentId = createCommentThroughApi(
                firstPostId, COMMENT_AUTHOR_ID, "Comment on first post"
        );

        mockMvc.perform(delete(commentLikesUrl(secondPostId, commentId))
                        .header("X-User-Id", LIKER_ID))
                .andExpect(status().isNotFound());

        assertEquals(0L, likeRepository.count());

        assertEquals(
                0L,
                countOutboxEvents(EventType.LIKE, ActionType.DELETED)
        );
    }

    @Test
    void unlikeComment_shouldRejectMissingUserIdHeader()
            throws Exception {

        Long postId = createPostThroughApi("Post for missing header");
        Long commentId = createCommentThroughApi(
                postId, COMMENT_AUTHOR_ID, "Comment to unlike"
        );

        mockMvc.perform(delete(commentLikesUrl(postId, commentId)))
                .andExpect(status().isBadRequest());

        assertEquals(0L, likeRepository.count());
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private String postLikesUrl(Long postId) {
        return POSTS_URL + "/" + postId + "/likes";
    }

    private String commentLikesUrl(Long postId, Long commentId) {
        return POSTS_URL + "/" + postId + "/comments/" + commentId + "/likes";
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
                .map(Post::getId)
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

        mockMvc.perform(post(POSTS_URL + "/" + postId + "/comments")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isCreated());

        return commentRepository.findAllByPost_Id(postId).stream()
                .filter(comment ->
                        userId.equals(comment.getUserId())
                                && content.equals(comment.getContent())
                )
                .map(Comment::getId)
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

    private void awaitOutboxPublished(Long eventId) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            OutboxEntry event = outboxRepository.findById(eventId)
                    .orElseThrow();

            assertTrue(
                    event.isPublished(),
                    "Outbox event should be published to Kafka"
            );
        });
    }
}