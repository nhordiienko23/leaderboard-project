package com.github.nhordiienko23.postservice.integration;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Like;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.service.LikeService;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("Like service transaction integration tests")
class LikeServiceTransactionIntegrationTest extends AbstractIntegrationTest {

    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_AUTHOR_ID = 31L;
    private static final Long LIKER_ID = 41L;

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanDatabaseAndKeepScheduledProcessorIdle() {
        likeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();

        // The Outbox repository is mocked in this test.
        // Keep the scheduled processor from processing a null queue.
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("Liking a post rolls back when saving its Outbox entry fails")
    void shouldRollBackPostLikeWhenOutboxSaveFails() {
        Post post = createPost();

        long likesBefore = likeRepository.count();

        doThrow(new IllegalStateException("Outbox save failed"))
                .when(outboxRepository)
                .save(any(OutboxEntry.class));

        assertThrows(
                IllegalStateException.class,
                () -> likeService.likePost(LIKER_ID, post.getId())
        );

        assertEquals(
                likesBefore,
                likeRepository.count(),
                "The like must not remain in PostgreSQL"
        );

        assertFalse(
                likeRepository.existsByUserIdAndPost_Id(
                        LIKER_ID,
                        post.getId()
                )
        );

        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Unliking a post rolls back when saving its Outbox entry fails")
    void shouldKeepPostLikeWhenOutboxSaveFailsDuringDeletion() {
        Post post = createPost();
        Like like = createPostLike(post);

        assertNotNull(like.getId());

        // The like previously earned points, so deletion must create a reversal event.
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        )).thenReturn(true);

        doThrow(new IllegalStateException("Outbox save failed"))
                .when(outboxRepository)
                .save(any(OutboxEntry.class));

        assertThrows(
                IllegalStateException.class,
                () -> likeService.unlikePost(LIKER_ID, post.getId())
        );

        assertTrue(
                likeRepository.existsById(like.getId()),
                "The like must remain in PostgreSQL after deletion fails"
        );

        assertTrue(
                likeRepository.existsByUserIdAndPost_Id(
                        LIKER_ID,
                        post.getId()
                )
        );

        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Liking a comment rolls back when saving its Outbox entry fails")
    void shouldRollBackCommentLikeWhenOutboxSaveFails() {
        Post post = createPost();
        Comment comment = createComment(post);

        long likesBefore = likeRepository.count();

        doThrow(new IllegalStateException("Outbox save failed"))
                .when(outboxRepository)
                .save(any(OutboxEntry.class));

        assertThrows(
                IllegalStateException.class,
                () -> likeService.likeComment(
                        LIKER_ID,
                        post.getId(),
                        comment.getId()
                )
        );

        assertEquals(
                likesBefore,
                likeRepository.count(),
                "The comment like must not remain in PostgreSQL"
        );

        assertFalse(
                likeRepository.existsByUserIdAndComment_Id(
                        LIKER_ID,
                        comment.getId()
                )
        );

        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Unliking a comment rolls back when saving its Outbox entry fails")
    void shouldKeepCommentLikeWhenOutboxSaveFailsDuringDeletion() {
        Post post = createPost();
        Comment comment = createComment(post);
        Like like = createCommentLike(comment);

        assertNotNull(like.getId());

        // The like previously earned points, so deletion must create a reversal event.
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        )).thenReturn(true);

        doThrow(new IllegalStateException("Outbox save failed"))
                .when(outboxRepository)
                .save(any(OutboxEntry.class));

        assertThrows(
                IllegalStateException.class,
                () -> likeService.unlikeComment(
                        LIKER_ID,
                        post.getId(),
                        comment.getId()
                )
        );

        assertTrue(
                likeRepository.existsById(like.getId()),
                "The comment like must remain in PostgreSQL after deletion fails"
        );

        assertTrue(
                likeRepository.existsByUserIdAndComment_Id(
                        LIKER_ID,
                        comment.getId()
                )
        );

        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    private Post createPost() {
        return postRepository.save(
                Post.builder()
                        .content("Post for like transaction test")
                        .userId(POST_AUTHOR_ID)
                        .build()
        );
    }

    private Comment createComment(Post post) {
        return commentRepository.save(
                Comment.builder()
                        .content("Comment for like transaction test")
                        .userId(COMMENT_AUTHOR_ID)
                        .post(post)
                        .build()
        );
    }

    private Like createPostLike(Post post) {
        return likeRepository.save(
                Like.builder()
                        .userId(LIKER_ID)
                        .post(post)
                        .build()
        );
    }

    private Like createCommentLike(Comment comment) {
        return likeRepository.save(
                Like.builder()
                        .userId(LIKER_ID)
                        .comment(comment)
                        .build()
        );
    }
}