package com.github.nhordiienko23.postservice.kafka;

import com.github.nhordiienko23.leaderboardcontract.event.*;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Like;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PostEventKafkaIntegrationTest extends AbstractIntegrationTest {

    private static final String POSTS_URL = "/api/posts";
    private static final String KAFKA_TOPIC = "leaderboard-events";

    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_AUTHOR_ID = 31L;
    private static final Long LIKER_ID = 41L;
    private static final Long COMMENT_LIKER_ID = 42L;
    private static final Long ADMIN_ID = 99L;

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
    private JsonMapper jsonMapper;

    /*
     * Kafka poll() can return several records at once. Keep records that have not
     * yet been matched to an expected event so a test does not accidentally drop them.
     */
    private final Deque<ConsumerRecord<String, byte[]>> bufferedRecords =
            new ArrayDeque<>();

    @BeforeEach
    void cleanDatabase() {
        bufferedRecords.clear();
        likeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // POST EVENTS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Creating a post publishes the correct POST CREATED event")
    void createPost_shouldPublishCorrectEventToKafka() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            seekToEnd(consumer);

            Post post = createPostThroughApi("Kafka post creation test");

            OutboxEntry outbox = findOutboxEvent(
                    post.getId(), EventType.POST, ActionType.CREATED
            );

            LeaderboardEvent event = consumeAndAssertEvent(consumer, outbox);

            assertEquals(
                    List.of(new ScoreParticipant(
                            POST_AUTHOR_ID, ParticipantRole.POST_AUTHOR
                    )),
                    event.participants()
            );
        }
    }

    @Test
    @DisplayName("Owner post deletion publishes only the post score reversal")
    void deletePost_ownerShouldPublishPostDeletionWithoutChildReversals()
            throws Exception {

        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post with children");
            Comment comment = createCommentThroughApi(
                    post.getId(), COMMENT_AUTHOR_ID, "Comment on post"
            );
            Like postLike = createPostLikeThroughApi(post.getId(), LIKER_ID);
            Like commentLike = createCommentLikeThroughApi(
                    post.getId(), comment.getId(), COMMENT_LIKER_ID
            );

            seekToEnd(consumer);

            mockMvc.perform(delete(postUrl(post.getId()))
                            .header("X-User-Id", POST_AUTHOR_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isNoContent());

            OutboxEntry postDeletion = findOutboxEvent(
                    post.getId(), EventType.POST, ActionType.DELETED
            );

            assertEquals(EventReason.USER_ACTION, postDeletion.getReason());
            assertEquals(POST_AUTHOR_ID, postDeletion.getActorId());

            LeaderboardEvent event = consumeAndAssertEvent(consumer, postDeletion);
            assertEquals(
                    List.of(new ScoreParticipant(
                            POST_AUTHOR_ID, ParticipantRole.POST_AUTHOR
                    )),
                    event.participants()
            );

            // Voluntary deletion does not reverse child-content scores.
            assertNoOutboxEvent(
                    comment.getId(), EventType.COMMENT, ActionType.DELETED
            );
            assertNoOutboxEvent(
                    postLike.getId(), EventType.LIKE, ActionType.DELETED
            );
            assertNoOutboxEvent(
                    commentLike.getId(), EventType.LIKE, ActionType.DELETED
            );

            assertNoKafkaRecords(consumer, Set.of(
                    key(EventType.COMMENT, comment.getId()),
                    key(EventType.LIKE, postLike.getId()),
                    key(EventType.LIKE, commentLike.getId())
            ));
        }
    }

    @Test
    @DisplayName("Admin post deletion publishes reversals for the post and its children")
    void deletePost_adminShouldPublishDeletionEventsForPostAndChildren()
            throws Exception {

        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post for moderation");
            Comment comment = createCommentThroughApi(
                    post.getId(), COMMENT_AUTHOR_ID, "Comment for moderation"
            );
            Like postLike = createPostLikeThroughApi(post.getId(), LIKER_ID);
            Like commentLike = createCommentLikeThroughApi(
                    post.getId(), comment.getId(), COMMENT_LIKER_ID
            );

            seekToEnd(consumer);

            mockMvc.perform(delete(postUrl(post.getId()))
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNoContent());

            OutboxEntry postDeletion = findOutboxEvent(
                    post.getId(), EventType.POST, ActionType.DELETED
            );
            OutboxEntry commentDeletion = findOutboxEvent(
                    comment.getId(), EventType.COMMENT, ActionType.DELETED
            );
            OutboxEntry postLikeDeletion = findOutboxEvent(
                    postLike.getId(), EventType.LIKE, ActionType.DELETED
            );
            OutboxEntry commentLikeDeletion = findOutboxEvent(
                    commentLike.getId(), EventType.LIKE, ActionType.DELETED
            );

            assertEquals(EventReason.MODERATION, postDeletion.getReason());
            assertEquals(EventReason.PARENT_DELETED, commentDeletion.getReason());
            assertEquals(EventReason.PARENT_DELETED, postLikeDeletion.getReason());
            assertEquals(EventReason.PARENT_DELETED, commentLikeDeletion.getReason());

            assertEquals(ADMIN_ID, postDeletion.getActorId());
            assertEquals(ADMIN_ID, commentDeletion.getActorId());
            assertEquals(ADMIN_ID, postLikeDeletion.getActorId());
            assertEquals(ADMIN_ID, commentLikeDeletion.getActorId());

            LeaderboardEvent postEvent =
                    consumeAndAssertEvent(consumer, postDeletion);
            LeaderboardEvent commentEvent =
                    consumeAndAssertEvent(consumer, commentDeletion);
            LeaderboardEvent postLikeEvent =
                    consumeAndAssertEvent(consumer, postLikeDeletion);
            LeaderboardEvent commentLikeEvent =
                    consumeAndAssertEvent(consumer, commentLikeDeletion);

            assertEquals(
                    List.of(new ScoreParticipant(
                            POST_AUTHOR_ID, ParticipantRole.POST_AUTHOR
                    )),
                    postEvent.participants()
            );
            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.COMMENT_AUTHOR
                            ),
                            new ScoreParticipant(
                                    POST_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    commentEvent.participants()
            );
            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    LIKER_ID, ParticipantRole.LIKER
                            ),
                            new ScoreParticipant(
                                    POST_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    postLikeEvent.participants()
            );
            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    COMMENT_LIKER_ID,
                                    ParticipantRole.LIKER
                            ),
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    commentLikeEvent.participants()
            );
        }
    }

    // -------------------------------------------------------------------------
    // COMMENT EVENTS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Creating a comment publishes the correct COMMENT CREATED event")
    void createComment_shouldPublishCorrectEventToKafka() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post for comment creation");
            seekToEnd(consumer);

            Comment comment = createCommentRequest(
                    post.getId(), COMMENT_AUTHOR_ID,
                    "Kafka comment creation test"
            );

            OutboxEntry outbox = findOutboxEvent(
                    comment.getId(), EventType.COMMENT, ActionType.CREATED
            );
            LeaderboardEvent event = consumeAndAssertEvent(consumer, outbox);

            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.COMMENT_AUTHOR
                            ),
                            new ScoreParticipant(
                                    POST_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    event.participants()
            );
            assertEquals(POST_AUTHOR_ID, post.getUserId());
        }
    }

    @Test
    @DisplayName("Commenting on your own post does not publish a scoring event")
    void createCommentOnOwnPost_shouldNotPublishScoringEvent() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("My post");
            seekToEnd(consumer);

            Comment comment = createCommentRequest(
                    post.getId(), POST_AUTHOR_ID, "My own comment"
            );

            assertNoOutboxEvent(
                    comment.getId(), EventType.COMMENT, ActionType.CREATED
            );
            assertNoKafkaRecords(
                    consumer, Set.of(key(EventType.COMMENT, comment.getId()))
            );
        }
    }

    @Test
    @DisplayName("Deleting a comment publishes a COMMENT DELETED event")
    void deleteComment_shouldPublishDeletionEventToKafka() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post for comment deletion");
            Comment comment = createCommentThroughApi(
                    post.getId(), COMMENT_AUTHOR_ID, "Comment to delete"
            );
            seekToEnd(consumer);

            mockMvc.perform(delete(commentUrl(post.getId(), comment.getId()))
                            .header("X-User-Id", COMMENT_AUTHOR_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isNoContent());

            OutboxEntry deletion = findOutboxEvent(
                    comment.getId(), EventType.COMMENT, ActionType.DELETED
            );
            assertEquals(EventReason.USER_ACTION, deletion.getReason());
            assertEquals(COMMENT_AUTHOR_ID, deletion.getActorId());

            LeaderboardEvent event = consumeAndAssertEvent(consumer, deletion);
            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.COMMENT_AUTHOR
                            ),
                            new ScoreParticipant(
                                    POST_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    event.participants()
            );
        }
    }

    @Test
    @DisplayName("Admin deleting a comment publishes moderation reversals for comment and likes")
    void deleteComment_adminShouldPublishModerationEventsForCommentAndLikes()
            throws Exception {

        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post for admin comment deletion");
            Comment comment = createCommentThroughApi(
                    post.getId(), COMMENT_AUTHOR_ID, "Comment to moderate"
            );
            Like like = createCommentLikeThroughApi(
                    post.getId(), comment.getId(), COMMENT_LIKER_ID
            );

            // Consume only records created after the deletion request.
            seekToEnd(consumer);

            mockMvc.perform(delete(commentUrl(post.getId(), comment.getId()))
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNoContent());

            OutboxEntry likeDeletion = findOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.DELETED
            );
            OutboxEntry commentDeletion = findOutboxEvent(
                    comment.getId(), EventType.COMMENT, ActionType.DELETED
            );

            assertEquals(EventReason.MODERATION, likeDeletion.getReason());
            assertEquals(EventReason.MODERATION, commentDeletion.getReason());
            assertEquals(ADMIN_ID, likeDeletion.getActorId());
            assertEquals(ADMIN_ID, commentDeletion.getActorId());

            LeaderboardEvent likeEvent =
                    consumeAndAssertEvent(consumer, likeDeletion);
            LeaderboardEvent commentEvent =
                    consumeAndAssertEvent(consumer, commentDeletion);

            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    COMMENT_LIKER_ID, ParticipantRole.LIKER
                            ),
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    likeEvent.participants()
            );
            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.COMMENT_AUTHOR
                            ),
                            new ScoreParticipant(
                                    POST_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    commentEvent.participants()
            );
        }
    }

    @Test
    @DisplayName("Deleting a comment with a like publishes both score reversals")
    void deleteCommentWithLike_shouldPublishCommentAndLikeReversals()
            throws Exception {

        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post for comment cleanup");
            Comment comment = createCommentThroughApi(
                    post.getId(), COMMENT_AUTHOR_ID, "Comment with like"
            );
            Like like = createCommentLikeThroughApi(
                    post.getId(), comment.getId(), COMMENT_LIKER_ID
            );
            seekToEnd(consumer);

            mockMvc.perform(delete(commentUrl(post.getId(), comment.getId()))
                            .header("X-User-Id", COMMENT_AUTHOR_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isNoContent());

            OutboxEntry likeDeletion = findOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.DELETED
            );
            OutboxEntry commentDeletion = findOutboxEvent(
                    comment.getId(), EventType.COMMENT, ActionType.DELETED
            );

            assertEquals(EventReason.USER_ACTION, likeDeletion.getReason());
            assertEquals(EventReason.USER_ACTION, commentDeletion.getReason());

            LeaderboardEvent likeEvent =
                    consumeAndAssertEvent(consumer, likeDeletion);
            LeaderboardEvent commentEvent =
                    consumeAndAssertEvent(consumer, commentDeletion);

            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    COMMENT_LIKER_ID, ParticipantRole.LIKER
                            ),
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    likeEvent.participants()
            );
            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.COMMENT_AUTHOR
                            ),
                            new ScoreParticipant(
                                    POST_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    commentEvent.participants()
            );
        }
    }

    // -------------------------------------------------------------------------
    // POST LIKE EVENTS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Liking another user's post publishes the correct LIKE CREATED event")
    void likePost_shouldPublishCorrectEventToKafka() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post to like");
            seekToEnd(consumer);

            Like like = createPostLikeRequest(post.getId(), LIKER_ID);
            OutboxEntry outbox = findOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.CREATED
            );
            LeaderboardEvent event = consumeAndAssertEvent(consumer, outbox);

            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    LIKER_ID, ParticipantRole.LIKER
                            ),
                            new ScoreParticipant(
                                    POST_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    event.participants()
            );
            assertEquals(POST_AUTHOR_ID, outbox.getContentAuthorId());
            assertEquals(POST_AUTHOR_ID, outbox.getTargetAuthorId());
        }
    }

    @Test
    @DisplayName("Unliking a post publishes the correct LIKE DELETED event")
    void unlikePost_shouldPublishDeletionEventToKafka() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post to unlike");
            Like like = createPostLikeThroughApi(post.getId(), LIKER_ID);
            seekToEnd(consumer);

            mockMvc.perform(delete(postLikesUrl(post.getId()))
                            .header("X-User-Id", LIKER_ID))
                    .andExpect(status().isNoContent());

            OutboxEntry deletion = findOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.DELETED
            );
            assertEquals(EventReason.USER_ACTION, deletion.getReason());
            assertEquals(LIKER_ID, deletion.getActorId());

            LeaderboardEvent event = consumeAndAssertEvent(consumer, deletion);
            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    LIKER_ID, ParticipantRole.LIKER
                            ),
                            new ScoreParticipant(
                                    POST_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    event.participants()
            );
        }
    }

    @Test
    @DisplayName("Liking your own post does not publish a scoring event")
    void likeOwnPost_shouldNotPublishScoringEvent() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("My own post");
            seekToEnd(consumer);

            Like like = createPostLikeRequest(post.getId(), POST_AUTHOR_ID);

            assertNoOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.CREATED
            );
            assertNoKafkaRecords(
                    consumer, Set.of(key(EventType.LIKE, like.getId()))
            );
        }
    }

    // -------------------------------------------------------------------------
    // COMMENT LIKE EVENTS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Liking another user's comment publishes the correct LIKE CREATED event")
    void likeComment_shouldPublishCorrectEventToKafka() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post for comment like");
            Comment comment = createCommentThroughApi(
                    post.getId(), COMMENT_AUTHOR_ID, "Comment to like"
            );
            seekToEnd(consumer);

            Like like = createCommentLikeRequest(
                    post.getId(), comment.getId(), LIKER_ID
            );
            OutboxEntry outbox = findOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.CREATED
            );
            LeaderboardEvent event = consumeAndAssertEvent(consumer, outbox);

            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    LIKER_ID, ParticipantRole.LIKER
                            ),
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    event.participants()
            );
            assertEquals(COMMENT_AUTHOR_ID, outbox.getContentAuthorId());
            assertEquals(COMMENT_AUTHOR_ID, outbox.getTargetAuthorId());
        }
    }

    @Test
    @DisplayName("Unliking a comment publishes the correct LIKE DELETED event")
    void unlikeComment_shouldPublishDeletionEventToKafka() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post for comment unlike");
            Comment comment = createCommentThroughApi(
                    post.getId(), COMMENT_AUTHOR_ID, "Comment to unlike"
            );
            Like like = createCommentLikeThroughApi(
                    post.getId(), comment.getId(), LIKER_ID
            );
            seekToEnd(consumer);

            mockMvc.perform(delete(commentLikesUrl(post.getId(), comment.getId()))
                            .header("X-User-Id", LIKER_ID))
                    .andExpect(status().isNoContent());

            OutboxEntry deletion = findOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.DELETED
            );
            assertEquals(EventReason.USER_ACTION, deletion.getReason());
            assertEquals(LIKER_ID, deletion.getActorId());
            assertEquals(COMMENT_AUTHOR_ID, deletion.getContentAuthorId());

            LeaderboardEvent event = consumeAndAssertEvent(consumer, deletion);
            assertEquals(
                    List.of(
                            new ScoreParticipant(
                                    LIKER_ID, ParticipantRole.LIKER
                            ),
                            new ScoreParticipant(
                                    COMMENT_AUTHOR_ID,
                                    ParticipantRole.CONTENT_AUTHOR
                            )
                    ),
                    event.participants()
            );
        }
    }

    @Test
    @DisplayName("Liking your own comment does not publish a scoring event")
    void likeOwnComment_shouldNotPublishScoringEvent() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = startConsumer()) {
            Post post = createPostThroughApi("Post with my comment");
            Comment comment = createCommentThroughApi(
                    post.getId(), COMMENT_AUTHOR_ID, "My own comment"
            );
            seekToEnd(consumer);

            Like like = createCommentLikeRequest(
                    post.getId(), comment.getId(), COMMENT_AUTHOR_ID
            );

            assertNoOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.CREATED
            );
            assertNoKafkaRecords(
                    consumer, Set.of(key(EventType.LIKE, like.getId()))
            );
        }
    }

    // -------------------------------------------------------------------------
    // HTTP AND DATA SETUP HELPERS
    // -------------------------------------------------------------------------

    private Post createPostThroughApi(String content) throws Exception {
        mockMvc.perform(post(POSTS_URL)
                        .header("X-User-Id", POST_AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isCreated());

        Post post = postRepository.findAll().stream()
                .filter(candidate -> content.equals(candidate.getContent()))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Created post was not persisted")
                );

        awaitOutboxPublished(findOutboxEvent(
                post.getId(), EventType.POST, ActionType.CREATED
        ).getId());

        return post;
    }

    private Comment createCommentThroughApi(
            Long postId,
            Long userId,
            String content
    ) throws Exception {
        Comment comment = createCommentRequest(postId, userId, content);
        Long postAuthorId = postRepository.findById(postId)
                .orElseThrow()
                .getUserId();

        if (!userId.equals(postAuthorId)) {
            awaitOutboxPublished(findOutboxEvent(
                    comment.getId(), EventType.COMMENT, ActionType.CREATED
            ).getId());
        }

        return comment;
    }

    private Comment createCommentRequest(
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

    private Like createPostLikeThroughApi(Long postId, Long userId)
            throws Exception {
        Like like = createPostLikeRequest(postId, userId);
        Long postAuthorId = postRepository.findById(postId)
                .orElseThrow()
                .getUserId();

        if (!userId.equals(postAuthorId)) {
            awaitOutboxPublished(findOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.CREATED
            ).getId());
        }

        return like;
    }

    private Like createPostLikeRequest(Long postId, Long userId)
            throws Exception {
        mockMvc.perform(post(postLikesUrl(postId))
                        .header("X-User-Id", userId))
                .andExpect(status().isCreated());

        return likeRepository.findByUserIdAndPost_Id(userId, postId)
                .orElseThrow(() ->
                        new AssertionError("Created post like was not persisted")
                );
    }

    private Like createCommentLikeThroughApi(
            Long postId,
            Long commentId,
            Long userId
    ) throws Exception {
        Like like = createCommentLikeRequest(postId, commentId, userId);
        Long commentAuthorId = commentRepository.findById(commentId)
                .orElseThrow()
                .getUserId();

        if (!userId.equals(commentAuthorId)) {
            awaitOutboxPublished(findOutboxEvent(
                    like.getId(), EventType.LIKE, ActionType.CREATED
            ).getId());
        }

        return like;
    }

    private Like createCommentLikeRequest(
            Long postId,
            Long commentId,
            Long userId
    ) throws Exception {
        mockMvc.perform(post(commentLikesUrl(postId, commentId))
                        .header("X-User-Id", userId))
                .andExpect(status().isCreated());

        return likeRepository.findByUserIdAndComment_Id(userId, commentId)
                .orElseThrow(() ->
                        new AssertionError("Created comment like was not persisted")
                );
    }

    // -------------------------------------------------------------------------
    // KAFKA CONSUMER HELPERS
    // -------------------------------------------------------------------------

    private KafkaConsumer<String, byte[]> startConsumer() {
        Properties properties = new Properties();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "post-event-it-" + UUID.randomUUID()
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class.getName()
        );
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(KAFKA_TOPIC));

        long deadline = System.nanoTime()
                + Duration.ofSeconds(10).toNanos();

        while (consumer.assignment().isEmpty()
                && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }

        assertFalse(
                consumer.assignment().isEmpty(),
                "Kafka consumer was not assigned a partition"
        );

        return consumer;
    }

    private void seekToEnd(KafkaConsumer<String, byte[]> consumer) {
        Set<TopicPartition> partitions = consumer.assignment();

        assertFalse(
                partitions.isEmpty(),
                "Consumer has no assigned partitions"
        );

        consumer.seekToEnd(partitions);

        for (TopicPartition partition : partitions) {
            consumer.position(partition);
        }

        bufferedRecords.clear();
    }

    private LeaderboardEvent consumeAndAssertEvent(
            KafkaConsumer<String, byte[]> consumer,
            OutboxEntry outbox
    ) throws Exception {
        awaitOutboxPublished(outbox.getId());

        String expectedKey = key(
                outbox.getEventType(),
                outbox.getContentId()
        );

        ConsumerRecord<String, byte[]> record =
                consumeRecord(consumer, expectedKey);

        assertEquals(outbox.getTopic(), record.topic());
        assertEquals(expectedKey, record.key());

        LeaderboardEvent event = jsonMapper.readValue(
                record.value(),
                LeaderboardEvent.class
        );

        assertNotNull(event);
        assertEquals(outbox.getId(), event.eventId());
        assertEquals(outbox.getEventType(), event.eventType());
        assertEquals(outbox.getActionType(), event.actionType());
        assertEquals(outbox.getReason(), event.reason());
        assertEquals(outbox.getContentId(), event.entityId());

        assertFalse(
                event.participants().isEmpty(),
                "Scoring event should contain participants"
        );

        return event;
    }

    /**
     * Reads a message without discarding other records returned by the same poll.
     */
    private ConsumerRecord<String, byte[]> consumeRecord(
            KafkaConsumer<String, byte[]> consumer,
            String expectedKey
    ) {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(20).toNanos();

        while (System.nanoTime() < deadline) {
            Iterator<ConsumerRecord<String, byte[]>> iterator =
                    bufferedRecords.iterator();

            while (iterator.hasNext()) {
                ConsumerRecord<String, byte[]> record = iterator.next();

                if (expectedKey.equals(record.key())) {
                    iterator.remove();
                    return record;
                }
            }

            ConsumerRecords<String, byte[]> records =
                    consumer.poll(Duration.ofMillis(250));

            for (ConsumerRecord<String, byte[]> record : records) {
                bufferedRecords.addLast(record);
            }
        }

        throw new AssertionError(
                "Kafka message not received: key=" + expectedKey
        );
    }

    private void assertNoKafkaRecords(
            KafkaConsumer<String, byte[]> consumer,
            Set<String> forbiddenKeys
    ) {
        long deadline = System.nanoTime()
                + Duration.ofMillis(2500).toNanos();

        for (ConsumerRecord<String, byte[]> record : bufferedRecords) {
            assertFalse(
                    forbiddenKeys.contains(record.key()),
                    "Unexpected Kafka message received: key=" + record.key()
            );
        }

        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, byte[]> records =
                    consumer.poll(Duration.ofMillis(250));

            for (ConsumerRecord<String, byte[]> record : records) {
                assertFalse(
                        forbiddenKeys.contains(record.key()),
                        "Unexpected Kafka message received: key=" + record.key()
                );

                bufferedRecords.addLast(record);
            }
        }
    }

    // -------------------------------------------------------------------------
    // OUTBOX HELPERS
    // -------------------------------------------------------------------------

    private OutboxEntry findOutboxEvent(
            Long contentId,
            EventType eventType,
            ActionType actionType
    ) {
        return outboxRepository.findAll().stream()
                .filter(entry ->
                        contentId.equals(entry.getContentId())
                                && eventType == entry.getEventType()
                                && actionType == entry.getActionType()
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

    private void assertNoOutboxEvent(
            Long contentId,
            EventType eventType,
            ActionType actionType
    ) {
        long matchingEntries = outboxRepository.findAll().stream()
                .filter(entry ->
                        contentId.equals(entry.getContentId())
                                && eventType == entry.getEventType()
                                && actionType == entry.getActionType()
                )
                .count();

        assertEquals(
                0L,
                matchingEntries,
                "Unexpected Outbox entry: contentId=" + contentId
                        + ", eventType=" + eventType
                        + ", actionType=" + actionType
        );
    }

    private void awaitOutboxPublished(Long eventId) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            OutboxEntry entry = outboxRepository.findById(eventId)
                    .orElseThrow();

            assertTrue(
                    entry.isPublished(),
                    "Outbox entry was not published: id=" + eventId
            );
            assertNotNull(entry.getPublishedAt());
            assertNull(entry.getLastError());
        });
    }

    // -------------------------------------------------------------------------
    // URL HELPERS
    // -------------------------------------------------------------------------

    private String key(EventType eventType, Long entityId) {
        return eventType + ":" + entityId;
    }

    private String postUrl(Long postId) {
        return POSTS_URL + "/" + postId;
    }

    private String postLikesUrl(Long postId) {
        return POSTS_URL + "/" + postId + "/likes";
    }

    private String commentsUrl(Long postId) {
        return POSTS_URL + "/" + postId + "/comments";
    }

    private String commentUrl(Long postId, Long commentId) {
        return commentsUrl(postId) + "/" + commentId;
    }

    private String commentLikesUrl(Long postId, Long commentId) {
        return commentUrl(postId, commentId) + "/likes";
    }
}