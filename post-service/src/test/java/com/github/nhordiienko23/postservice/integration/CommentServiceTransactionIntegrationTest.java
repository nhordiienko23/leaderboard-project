package com.github.nhordiienko23.postservice.integration;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.dto.CommentCreationRequest;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.service.CommentService;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("Comment service transaction integration tests")
class CommentServiceTransactionIntegrationTest
        extends AbstractIntegrationTest {

    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_AUTHOR_ID = 31L;

    private static final String CREATE_CONTENT =
            "Comment must be rolled back after Outbox failure";

    private static final String DELETE_CONTENT =
            "Comment must remain after deletion failure";

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private OutboxRepository outboxRepository;

    @BeforeEach
    void keepScheduledOutboxProcessorIdle() {
        // The Outbox processor is scheduled in the Spring context.
        // Keep its queue empty because the repository is mocked.
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("Creating a comment rolls back when saving its Outbox entry fails")
    void shouldRollBackCommentWhenOutboxSaveFails() {
        Post post = createPost();

        long commentsBefore = commentRepository.count();

        doThrow(new IllegalStateException("Outbox save failed"))
                .when(outboxRepository)
                .save(any(OutboxEntry.class));

        assertThrows(
                IllegalStateException.class,
                () -> commentService.createComment(
                        COMMENT_AUTHOR_ID,
                        post.getId(),
                        new CommentCreationRequest(CREATE_CONTENT)
                )
        );

        assertEquals(
                commentsBefore,
                commentRepository.count(),
                "The comment must not remain in PostgreSQL"
        );

        assertTrue(
                commentRepository.findAll().stream()
                        .noneMatch(comment ->
                                CREATE_CONTENT.equals(comment.getContent())
                        ),
                "The failed comment must not exist"
        );

        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Deleting a comment rolls back when saving its Outbox entry fails")
    void shouldKeepCommentWhenOutboxSaveFailsDuringDeletion() {
        Post post = createPost();

        Comment comment = commentRepository.save(
                Comment.builder()
                        .content(DELETE_CONTENT)
                        .userId(COMMENT_AUTHOR_ID)
                        .post(post)
                        .build()
        );

        Long commentId = comment.getId();

        assertTrue(commentRepository.existsById(commentId));

        // Pretend the comment creation event exists, so the service attempts
        // to enqueue the corresponding deletion event.
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                commentId,
                EventType.COMMENT,
                ActionType.CREATED
        )).thenReturn(true);

        doThrow(new IllegalStateException("Outbox save failed"))
                .when(outboxRepository)
                .save(any(OutboxEntry.class));

        assertThrows(
                IllegalStateException.class,
                () -> commentService.deleteComment(
                        COMMENT_AUTHOR_ID,
                        UserRole.USER,
                        post.getId(),
                        commentId
                )
        );

        assertTrue(
                commentRepository.existsById(commentId),
                "The comment must remain in PostgreSQL after deletion fails"
        );

        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    private Post createPost() {
        return postRepository.save(
                Post.builder()
                        .content("Parent post for comment transaction test")
                        .userId(POST_AUTHOR_ID)
                        .build()
        );
    }
}