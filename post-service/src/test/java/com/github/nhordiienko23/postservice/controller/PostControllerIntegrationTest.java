package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.dto.CommentCreationRequest;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.service.CommentService;
import com.github.nhordiienko23.postservice.service.LikeService;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PostControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String POSTS_URL = "/api/posts";

    private static final Long AUTHOR_ID = 23L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ADMIN_ID = 99L;
    private static final Long COMMENT_AUTHOR_ID = 31L;
    private static final Long POST_LIKER_ID = 41L;
    private static final Long COMMENT_LIKER_ID = 42L;

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
    private CommentService commentService;

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
    void createPost_shouldPersistPostAndPublishOutboxEvent()
            throws Exception {

        String content = "Integration test post";

        mockMvc.perform(post(POSTS_URL)
                        .header("X-User-Id", AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Integration test post"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.content").value(content))
                .andExpect(jsonPath("$.userId").value(AUTHOR_ID));

        Post savedPost = postRepository.findAll().stream()
                .filter(post -> content.equals(post.getContent()))
                .findFirst()
                .orElseThrow();

        OutboxEntry event = findOutboxEvent(
                savedPost.getId(),
                EventType.POST,
                ActionType.CREATED
        );

        assertEquals(AUTHOR_ID, event.getActorId());
        assertEquals(AUTHOR_ID, event.getContentAuthorId());
        assertEquals(EventReason.USER_ACTION, event.getReason());

        // The real OutboxProcessor marks the event published only after
        // the Kafka producer successfully sends it.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            OutboxEntry published = outboxRepository.findById(event.getId())
                    .orElseThrow();

            assertTrue(published.isPublished());
        });
    }

    @Test
    void createPost_shouldRejectBlankContent() throws Exception {
        mockMvc.perform(post(POSTS_URL)
                        .header("X-User-Id", AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0L, postRepository.count());
        assertEquals(0L, outboxRepository.count());
    }

    @Test
    void createPost_shouldRejectContentLongerThan255Characters()
            throws Exception {

        String tooLongContent = "x".repeat(256);

        mockMvc.perform(post(POSTS_URL)
                        .header("X-User-Id", AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(tooLongContent)))
                .andExpect(status().isBadRequest());

        assertEquals(0L, postRepository.count());
        assertEquals(0L, outboxRepository.count());
    }

    @Test
    void createPost_shouldRejectMissingUserIdHeader() throws Exception {
        mockMvc.perform(post(POSTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Missing user header"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0L, postRepository.count());
        assertEquals(0L, outboxRepository.count());
    }

    @Test
    void createPost_shouldRejectInvalidUserIdHeader() throws Exception {
        mockMvc.perform(post(POSTS_URL)
                        .header("X-User-Id", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Invalid user header"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0L, postRepository.count());
        assertEquals(0L, outboxRepository.count());
    }

    @Test
    void createPost_shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post(POSTS_URL)
                        .header("X-User-Id", AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0L, postRepository.count());
        assertEquals(0L, outboxRepository.count());
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Test
    void getPost_shouldReturnPost() throws Exception {
        Long postId = createPostThroughApi("Post to read");

        mockMvc.perform(get(POSTS_URL + "/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.intValue()))
                .andExpect(jsonPath("$.content").value("Post to read"))
                .andExpect(jsonPath("$.userId").value(AUTHOR_ID));
    }

    @Test
    void getPost_shouldReturn404WhenPostDoesNotExist() throws Exception {
        mockMvc.perform(get(POSTS_URL + "/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPost_shouldReturn400WhenIdIsNotNumeric() throws Exception {
        mockMvc.perform(get(POSTS_URL + "/{id}", "not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllPosts_shouldReturnEmptyPageWhenNoPostsExist()
            throws Exception {

        mockMvc.perform(get(POSTS_URL)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    @Test
    void getAllPosts_shouldReturnCorrectPaginationMetadata()
            throws Exception {

        createPostThroughApi("First post");
        createPostThroughApi("Second post");

        mockMvc.perform(get(POSTS_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void getAllPosts_shouldReturnOnlyRequestedPage()
            throws Exception {

        createPostThroughApi("First post");
        createPostThroughApi("Second post");

        mockMvc.perform(get(POSTS_URL)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        mockMvc.perform(get(POSTS_URL)
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Test
    void updatePost_ownerShouldUpdateContent() throws Exception {
        Long postId = createPostThroughApi("Original content");

        mockMvc.perform(put(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated content"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated content"))
                .andExpect(jsonPath("$.userId").value(AUTHOR_ID));

        assertEquals(
                "Updated content",
                postRepository.findById(postId).orElseThrow().getContent()
        );

        // Updating post text must not generate scoring events.
        assertEquals(1L, outboxRepository.count());
    }

    @Test
    void updatePost_adminShouldUpdateAnotherUsersPost()
            throws Exception {

        Long postId = createPostThroughApi("Original content");

        mockMvc.perform(put(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated by admin"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated by admin"));

        assertEquals(
                "Updated by admin",
                postRepository.findById(postId).orElseThrow().getContent()
        );

        assertEquals(1L, outboxRepository.count());
    }

    @Test
    void updatePost_shouldRejectNonOwner() throws Exception {
        Long postId = createPostThroughApi("Original content");

        mockMvc.perform(put(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", OTHER_USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Unauthorized update"
                                }
                                """))
                .andExpect(status().isForbidden());

        assertEquals(
                "Original content",
                postRepository.findById(postId).orElseThrow().getContent()
        );

        assertEquals(1L, outboxRepository.count());
    }

    @Test
    void updatePost_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(put(POSTS_URL + "/{id}", 999999L)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated content"
                                }
                                """))
                .andExpect(status().isNotFound());

        assertEquals(0L, outboxRepository.count());
    }

    @Test
    void updatePost_shouldRejectBlankContent() throws Exception {
        Long postId = createPostThroughApi("Original content");

        mockMvc.perform(put(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(
                "Original content",
                postRepository.findById(postId).orElseThrow().getContent()
        );
    }

    @Test
    void updatePost_shouldRejectMissingRoleHeader() throws Exception {
        Long postId = createPostThroughApi("Original content");

        mockMvc.perform(put(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated content"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(
                "Original content",
                postRepository.findById(postId).orElseThrow().getContent()
        );
    }

    @Test
    void updatePost_shouldRejectUnknownRole() throws Exception {
        Long postId = createPostThroughApi("Original content");

        mockMvc.perform(put(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated content"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(
                "Original content",
                postRepository.findById(postId).orElseThrow().getContent()
        );
    }

    @Test
    void updatePost_shouldRejectContentLongerThan255Characters()
            throws Exception {

        Long postId = createPostThroughApi("Original content");
        String tooLongContent = "x".repeat(256);

        mockMvc.perform(put(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(tooLongContent)))
                .andExpect(status().isBadRequest());

        assertEquals(
                "Original content",
                postRepository.findById(postId).orElseThrow().getContent()
        );
    }

    // -------------------------------------------------------------------------
    // DELETE: OWNER
    // -------------------------------------------------------------------------

    @Test
    void deletePost_ownerShouldDeletePostAndCreateDeletionEvent()
            throws Exception {

        Long postId = createPostThroughApi("Post to delete");

        mockMvc.perform(delete(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNoContent());

        assertTrue(postRepository.findById(postId).isEmpty());

        OutboxEntry deletionEvent = findOutboxEvent(
                postId,
                EventType.POST,
                ActionType.DELETED
        );

        assertEquals(EventReason.USER_ACTION, deletionEvent.getReason());
        assertEquals(AUTHOR_ID, deletionEvent.getActorId());
        assertEquals(AUTHOR_ID, deletionEvent.getContentAuthorId());
    }

    @Test
    void deletePost_ownerShouldRemoveChildrenWithoutScoreReversal()
            throws Exception {

        Long postId = createPostThroughApi("Post with children");

        Long commentId = createCommentThroughService(
                postId,
                COMMENT_AUTHOR_ID,
                "Scored comment"
        );

        likeService.likePost(POST_LIKER_ID, postId);
        likeService.likeComment(COMMENT_LIKER_ID, postId, commentId);

        assertEquals(1, commentRepository.findAllByPost_Id(postId).size());
        assertEquals(1, likeRepository.findAllByPost_Id(postId).size());
        assertEquals(1, likeRepository.findAllByComment_Id(commentId).size());

        mockMvc.perform(delete(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNoContent());

        assertTrue(postRepository.findById(postId).isEmpty());
        assertTrue(commentRepository.findAllByPost_Id(postId).isEmpty());
        assertTrue(likeRepository.findAllByPost_Id(postId).isEmpty());
        assertTrue(likeRepository.findAllByComment_Id(commentId).isEmpty());

        // Owner deletion must not enqueue score reversals for child content.
        List<OutboxEntry> deletionEvents = outboxRepository.findAll().stream()
                .filter(event -> event.getActionType() == ActionType.DELETED)
                .toList();

        assertEquals(1, deletionEvents.size());
        assertEquals(EventType.POST, deletionEvents.getFirst().getEventType());
        assertEquals(EventReason.USER_ACTION, deletionEvents.getFirst().getReason());
    }

    // -------------------------------------------------------------------------
    // DELETE: MODERATION
    // -------------------------------------------------------------------------

    @Test
    void deletePost_adminShouldReversePostCommentAndLikeEvents()
            throws Exception {

        Long postId = createPostThroughApi("Post for moderation");

        Long commentId = createCommentThroughService(
                postId,
                COMMENT_AUTHOR_ID,
                "Comment that earned points"
        );

        likeService.likePost(POST_LIKER_ID, postId);
        likeService.likeComment(COMMENT_LIKER_ID, postId, commentId);

        // Before moderation there are four scoring events:
        // POST/CREATED, COMMENT/CREATED and two LIKE/CREATED.
        assertEquals(4L, outboxRepository.count());

        mockMvc.perform(delete(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());

        assertTrue(postRepository.findById(postId).isEmpty());
        assertTrue(commentRepository.findAllByPost_Id(postId).isEmpty());
        assertTrue(likeRepository.findAllByPost_Id(postId).isEmpty());
        assertTrue(likeRepository.findAllByComment_Id(commentId).isEmpty());

        List<OutboxEntry> deletionEvents = outboxRepository.findAll().stream()
                .filter(event -> event.getActionType() == ActionType.DELETED)
                .toList();

        // One post, one comment and two likes have their scores reversed.
        assertEquals(4, deletionEvents.size());

        assertEquals(
                1,
                deletionEvents.stream()
                        .filter(event -> event.getEventType() == EventType.POST)
                        .count()
        );
        assertEquals(
                1,
                deletionEvents.stream()
                        .filter(event -> event.getEventType() == EventType.COMMENT)
                        .count()
        );
        assertEquals(
                2,
                deletionEvents.stream()
                        .filter(event -> event.getEventType() == EventType.LIKE)
                        .count()
        );

        OutboxEntry postDeletion = findOutboxEvent(
                postId,
                EventType.POST,
                ActionType.DELETED
        );

        assertEquals(EventReason.MODERATION, postDeletion.getReason());
        assertEquals(ADMIN_ID, postDeletion.getActorId());

        OutboxEntry commentDeletion = findOutboxEvent(
                commentId,
                EventType.COMMENT,
                ActionType.DELETED
        );

        assertEquals(EventReason.PARENT_DELETED, commentDeletion.getReason());
        assertEquals(ADMIN_ID, commentDeletion.getActorId());

        deletionEvents.stream()
                .filter(event -> event.getEventType() == EventType.LIKE)
                .forEach(event -> {
                    assertEquals(
                            EventReason.PARENT_DELETED,
                            event.getReason()
                    );
                    assertEquals(ADMIN_ID, event.getActorId());
                });
    }

    @Test
    void deletePost_shouldRejectNonOwner() throws Exception {
        Long postId = createPostThroughApi("Protected post");

        mockMvc.perform(delete(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", OTHER_USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        assertTrue(postRepository.findById(postId).isPresent());

        assertEquals(
                1L,
                outboxRepository.findAll().stream()
                        .filter(event -> event.getActionType() == ActionType.CREATED)
                        .count()
        );

        assertEquals(
                0L,
                outboxRepository.findAll().stream()
                        .filter(event -> event.getActionType() == ActionType.DELETED)
                        .count()
        );
    }

    @Test
    void deletePost_adminShouldMarkReasonAsModeration()
            throws Exception {

        Long postId = createPostThroughApi("Post for moderation");

        mockMvc.perform(delete(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());

        assertTrue(postRepository.findById(postId).isEmpty());

        OutboxEntry deletionEvent = findOutboxEvent(
                postId,
                EventType.POST,
                ActionType.DELETED
        );

        assertEquals(EventReason.MODERATION, deletionEvent.getReason());
        assertEquals(ADMIN_ID, deletionEvent.getActorId());
    }

    @Test
    void deletePost_shouldRejectMissingRoleHeader() throws Exception {
        Long postId = createPostThroughApi("Protected post");

        mockMvc.perform(delete(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID))
                .andExpect(status().isBadRequest());

        assertTrue(postRepository.findById(postId).isPresent());
        assertEquals(1L, outboxRepository.count());
    }

    @Test
    void deletePost_shouldRejectUnknownRole() throws Exception {
        Long postId = createPostThroughApi("Protected post");

        mockMvc.perform(delete(POSTS_URL + "/{id}", postId)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isBadRequest());

        assertTrue(postRepository.findById(postId).isPresent());
        assertEquals(1L, outboxRepository.count());
    }

    @Test
    void deletePost_shouldReturn404WhenPostDoesNotExist()
            throws Exception {

        mockMvc.perform(delete(POSTS_URL + "/{id}", 999999L)
                        .header("X-User-Id", AUTHOR_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());

        assertEquals(0L, outboxRepository.count());
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private Long createPostThroughApi(String content) throws Exception {
        mockMvc.perform(post(POSTS_URL)
                        .header("X-User-Id", AUTHOR_ID)
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

    private Long createCommentThroughService(
            Long postId,
            Long commentAuthorId,
            String content
    ) {
        commentService.createComment(
                commentAuthorId,
                postId,
                new CommentCreationRequest(content)
        );

        Comment comment = commentRepository.findAllByPost_Id(postId).stream()
                .filter(candidate ->
                        commentAuthorId.equals(candidate.getUserId())
                                && content.equals(candidate.getContent())
                )
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Created comment was not persisted")
                );

        return comment.getId();
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
}