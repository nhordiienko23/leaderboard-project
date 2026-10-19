package com.github.nhordiienko23.postservice.integration;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.service.PostService;
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
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("Post service transaction integration tests")
class PostServiceTransactionIntegrationTest extends AbstractIntegrationTest {

    private static final Long AUTHOR_ID = 23L;

    private static final String CREATE_CONTENT =
            "Post must be rolled back after Outbox failure";

    private static final String DELETE_CONTENT =
            "Post must remain after deletion failure";

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private OutboxRepository outboxRepository;

    @BeforeEach
    void keepScheduledOutboxProcessorIdle() {
        // Prevent the scheduled processor from attempting to publish events
        // while the Outbox repository is mocked.
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("Creating a post rolls back when saving its Outbox entry fails")
    void shouldRollBackPostWhenOutboxSaveFails() {
        long postsBefore = postRepository.count();

        doThrow(new IllegalStateException("Outbox save failed"))
                .when(outboxRepository)
                .save(any(OutboxEntry.class));

        assertThrows(
                IllegalStateException.class,
                () -> postService.createPost(
                        AUTHOR_ID,
                        new PostCreationRequest(CREATE_CONTENT)
                )
        );

        assertEquals(postsBefore, postRepository.count());

        assertTrue(postRepository.findAll().stream()
                .noneMatch(post -> CREATE_CONTENT.equals(post.getContent())));

        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Deleting a post keeps it in the database when Outbox saving fails")
    void shouldKeepPostWhenOutboxSaveFailsDuringDeletion() {
        PostDTO createdPost = postService.createPost(
                AUTHOR_ID,
                new PostCreationRequest(DELETE_CONTENT)
        );

        Long postId = createdPost.id();

        assertTrue(postRepository.existsById(postId));

        // Ignore interactions performed while creating the post.
        clearInvocations(outboxRepository);

        doThrow(new IllegalStateException("Outbox save failed"))
                .when(outboxRepository)
                .save(any(OutboxEntry.class));

        assertThrows(
                IllegalStateException.class,
                () -> postService.deletePost(
                        AUTHOR_ID,
                        UserRole.USER,
                        postId
                )
        );

        assertTrue(
                postRepository.existsById(postId),
                "The post must remain in PostgreSQL after deletion fails"
        );

        verify(outboxRepository).save(any(OutboxEntry.class));
    }
}