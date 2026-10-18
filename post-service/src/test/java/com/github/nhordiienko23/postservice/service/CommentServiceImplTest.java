package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.dto.CommentCreationRequest;
import com.github.nhordiienko23.postservice.dto.CommentDTO;
import com.github.nhordiienko23.postservice.dto.CommentUpdateRequest;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.exception.notfound.CommentNotFoundException;
import com.github.nhordiienko23.postservice.exception.notfound.PostNotFoundException;
import com.github.nhordiienko23.postservice.mapper.CommentMapper;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    private static final Long POST_ID = 10L;
    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ADMIN_ID = 99L;
    private static final Long COMMENT_ID = 20L;

    private static final String PARTICIPANTS_JSON =
            "[{\"userId\":1,\"role\":\"COMMENT_AUTHOR\"}]";

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxEntryMapper outboxEntryMapper;

    @Mock
    private LeaderboardParticipantsJsonFactory participantsJsonFactory;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private LikeService likeService;

    @InjectMocks
    private CommentServiceImpl commentService;

    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        post = createPost(POST_ID, POST_AUTHOR_ID);
        comment = createComment(COMMENT_ID, COMMENT_AUTHOR_ID, post);
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Test
    void createComment_shouldSaveCommentAndCreateOutboxEvent() {
        CommentCreationRequest request =
                new CommentCreationRequest("Test comment");

        OutboxEntry createdEvent = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class)))
                .thenReturn(comment);
        when(participantsJsonFactory.forComment(
                COMMENT_AUTHOR_ID,
                POST_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);
        when(outboxEntryMapper.toCreatedCommentOutboxEntry(
                comment,
                PARTICIPANTS_JSON
        )).thenReturn(createdEvent);

        CommentDTO result = commentService.createComment(
                COMMENT_AUTHOR_ID,
                POST_ID,
                request
        );

        verify(commentRepository).save(any(Comment.class));
        verify(participantsJsonFactory).forComment(
                COMMENT_AUTHOR_ID,
                POST_AUTHOR_ID
        );
        verify(outboxEntryMapper).toCreatedCommentOutboxEntry(
                comment,
                PARTICIPANTS_JSON
        );
        verify(outboxRepository).save(createdEvent);
        verify(commentMapper).toDTO(comment);
    }

    @Test
    void createComment_shouldNotCreateScoreEventForPostAuthorCommentingOnOwnPost() {
        CommentCreationRequest request =
                new CommentCreationRequest("Self comment");

        Comment selfComment = createComment(
                COMMENT_ID,
                POST_AUTHOR_ID,
                post
        );

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class)))
                .thenReturn(selfComment);

        commentService.createComment(
                POST_AUTHOR_ID,
                POST_ID,
                request
        );

        verify(commentRepository).save(any(Comment.class));
        verify(commentMapper).toDTO(selfComment);

        verifyNoInteractions(
                participantsJsonFactory,
                outboxEntryMapper,
                outboxRepository
        );
    }

    @Test
    void createComment_shouldThrowWhenPostDoesNotExist() {
        CommentCreationRequest request =
                new CommentCreationRequest("Test comment");

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> commentService.createComment(
                        COMMENT_AUTHOR_ID,
                        POST_ID,
                        request
                )
        );

        verifyNoInteractions(
                commentRepository,
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory,
                commentMapper
        );
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Test
    void getComments_shouldReturnMappedPage() {
        var pageable = PageRequest.of(0, 10);
        Page<Comment> comments = new PageImpl<>(
                List.of(comment),
                pageable,
                1
        );

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findAllByPost_Id(POST_ID, pageable))
                .thenReturn(comments);

        Page<CommentDTO> result =
                commentService.getComments(POST_ID, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        verify(commentRepository)
                .findAllByPost_Id(POST_ID, pageable);
        verify(commentMapper).toDTO(comment);
    }

    @Test
    void getComments_shouldThrowWhenPostDoesNotExist() {
        var pageable = PageRequest.of(0, 10);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> commentService.getComments(POST_ID, pageable)
        );

        verify(commentRepository, never())
                .findAllByPost_Id(anyLong(), any());
        verifyNoInteractions(commentMapper);
    }

    @Test
    void getComment_shouldReturnMappedComment() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(comment));

        CommentDTO result =
                commentService.getComment(POST_ID, COMMENT_ID);

        verify(commentRepository).findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        );
        verify(commentMapper).toDTO(comment);
    }

    @Test
    void getComment_shouldThrowWhenPostDoesNotExist() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> commentService.getComment(POST_ID, COMMENT_ID)
        );

        verify(commentRepository, never())
                .findByIdAndPost_Id(anyLong(), anyLong());
        verifyNoInteractions(commentMapper);
    }

    @Test
    void getComment_shouldThrowWhenCommentDoesNotExist() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.empty());

        assertThrows(
                CommentNotFoundException.class,
                () -> commentService.getComment(POST_ID, COMMENT_ID)
        );

        verifyNoInteractions(commentMapper);
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Test
    void updateComment_ownerShouldUpdateContent() {
        String updatedContent = "Updated comment";
        CommentUpdateRequest request =
                new CommentUpdateRequest(updatedContent);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment))
                .thenReturn(comment);

        commentService.updateComment(
                COMMENT_AUTHOR_ID,
                UserRole.USER,
                POST_ID,
                COMMENT_ID,
                request
        );

        assertEquals(updatedContent, comment.getContent());

        verify(commentRepository).save(comment);
        verify(commentMapper).toDTO(comment);
        verifyNoInteractions(outboxRepository, likeService);
    }

    @Test
    void updateComment_adminShouldUpdateAnotherUsersComment() {
        String updatedContent = "Moderated comment";
        CommentUpdateRequest request =
                new CommentUpdateRequest(updatedContent);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment))
                .thenReturn(comment);

        commentService.updateComment(
                ADMIN_ID,
                UserRole.ADMIN,
                POST_ID,
                COMMENT_ID,
                request
        );

        assertEquals(updatedContent, comment.getContent());

        verify(commentRepository).save(comment);
        verify(commentMapper).toDTO(comment);
    }

    @Test
    void updateComment_shouldRejectUserWhoDoesNotOwnComment() {
        CommentUpdateRequest request =
                new CommentUpdateRequest("Unauthorized update");

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(comment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> commentService.updateComment(
                        OTHER_USER_ID,
                        UserRole.USER,
                        POST_ID,
                        COMMENT_ID,
                        request
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(commentRepository, never()).save(any(Comment.class));
        verifyNoInteractions(commentMapper, outboxRepository, likeService);
    }

    @Test
    void updateComment_shouldThrowWhenPostDoesNotExist() {
        CommentUpdateRequest request =
                new CommentUpdateRequest("Updated comment");

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> commentService.updateComment(
                        COMMENT_AUTHOR_ID,
                        UserRole.USER,
                        POST_ID,
                        COMMENT_ID,
                        request
                )
        );

        verify(commentRepository, never())
                .findByIdAndPost_Id(anyLong(), anyLong());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void updateComment_shouldThrowWhenCommentDoesNotExist() {
        CommentUpdateRequest request =
                new CommentUpdateRequest("Updated comment");

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.empty());

        assertThrows(
                CommentNotFoundException.class,
                () -> commentService.updateComment(
                        COMMENT_AUTHOR_ID,
                        UserRole.USER,
                        POST_ID,
                        COMMENT_ID,
                        request
                )
        );

        verify(commentRepository, never()).save(any(Comment.class));
        verifyNoInteractions(commentMapper);
    }

    // -------------------------------------------------------------------------
    // DELETE: NORMAL USER ACTION
    // -------------------------------------------------------------------------

    @Test
    void deleteComment_ownerShouldReverseCommentAndLikeScores() {
        OutboxEntry deletedEvent = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(comment));

        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                COMMENT_ID,
                EventType.COMMENT,
                ActionType.CREATED
        )).thenReturn(true);

        when(participantsJsonFactory.forComment(
                COMMENT_AUTHOR_ID,
                POST_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);

        when(outboxEntryMapper.toDeletedCommentOutboxEntry(
                comment,
                COMMENT_AUTHOR_ID,
                PARTICIPANTS_JSON,
                EventReason.USER_ACTION
        )).thenReturn(deletedEvent);

        commentService.deleteComment(
                COMMENT_AUTHOR_ID,
                UserRole.USER,
                POST_ID,
                COMMENT_ID
        );

        verify(likeService).removeLikesForComment(
                comment,
                COMMENT_AUTHOR_ID,
                EventReason.USER_ACTION
        );

        verify(outboxRepository).existsByContentIdAndEventTypeAndActionType(
                COMMENT_ID,
                EventType.COMMENT,
                ActionType.CREATED
        );

        verify(participantsJsonFactory).forComment(
                COMMENT_AUTHOR_ID,
                POST_AUTHOR_ID
        );

        verify(outboxEntryMapper).toDeletedCommentOutboxEntry(
                comment,
                COMMENT_AUTHOR_ID,
                PARTICIPANTS_JSON,
                EventReason.USER_ACTION
        );

        verify(outboxRepository).save(deletedEvent);
        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_adminShouldUseModerationReason() {
        OutboxEntry deletedEvent = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(comment));

        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                COMMENT_ID,
                EventType.COMMENT,
                ActionType.CREATED
        )).thenReturn(true);

        when(participantsJsonFactory.forComment(
                COMMENT_AUTHOR_ID,
                POST_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);

        when(outboxEntryMapper.toDeletedCommentOutboxEntry(
                comment,
                ADMIN_ID,
                PARTICIPANTS_JSON,
                EventReason.MODERATION
        )).thenReturn(deletedEvent);

        commentService.deleteComment(
                ADMIN_ID,
                UserRole.ADMIN,
                POST_ID,
                COMMENT_ID
        );

        verify(likeService).removeLikesForComment(
                comment,
                ADMIN_ID,
                EventReason.MODERATION
        );

        verify(outboxEntryMapper).toDeletedCommentOutboxEntry(
                comment,
                ADMIN_ID,
                PARTICIPANTS_JSON,
                EventReason.MODERATION
        );

        verify(outboxRepository).save(deletedEvent);
        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_shouldSkipReversalWhenCreationWasNotScored() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(comment));

        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                COMMENT_ID,
                EventType.COMMENT,
                ActionType.CREATED
        )).thenReturn(false);

        commentService.deleteComment(
                COMMENT_AUTHOR_ID,
                UserRole.USER,
                POST_ID,
                COMMENT_ID
        );

        // The dependent likes are cleaned up independently.
        verify(likeService).removeLikesForComment(
                comment,
                COMMENT_AUTHOR_ID,
                EventReason.USER_ACTION
        );

        // There is no corresponding COMMENT/CREATED event to reverse.
        verify(outboxEntryMapper, never())
                .toDeletedCommentOutboxEntry(
                        any(Comment.class),
                        anyLong(),
                        anyString(),
                        any(EventReason.class)
                );

        verify(outboxRepository, never()).save(any(OutboxEntry.class));
        verify(commentRepository).delete(comment);
        verifyNoInteractions(participantsJsonFactory);
    }

    @Test
    void deleteComment_selfCommentShouldNotCreateCommentReversalEvent() {
        Comment selfComment = createComment(
                COMMENT_ID,
                POST_AUTHOR_ID,
                post
        );

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(selfComment));

        commentService.deleteComment(
                POST_AUTHOR_ID,
                UserRole.USER,
                POST_ID,
                COMMENT_ID
        );

        // Likes on the comment are still cleaned up.
        verify(likeService).removeLikesForComment(
                selfComment,
                POST_AUTHOR_ID,
                EventReason.USER_ACTION
        );

        // A self-comment did not earn comment points.
        verify(outboxRepository, never())
                .existsByContentIdAndEventTypeAndActionType(
                        anyLong(),
                        any(EventType.class),
                        any(ActionType.class)
                );

        verify(outboxEntryMapper, never())
                .toDeletedCommentOutboxEntry(
                        any(Comment.class),
                        anyLong(),
                        anyString(),
                        any(EventReason.class)
                );

        verify(outboxRepository, never()).save(any(OutboxEntry.class));
        verify(commentRepository).delete(selfComment);
    }

    // -------------------------------------------------------------------------
    // DELETE: ACCESS CONTROL AND NOT FOUND
    // -------------------------------------------------------------------------

    @Test
    void deleteComment_shouldRejectUserWhoDoesNotOwnComment() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.of(comment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> commentService.deleteComment(
                        OTHER_USER_ID,
                        UserRole.USER,
                        POST_ID,
                        COMMENT_ID
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(commentRepository, never()).delete(any(Comment.class));
        verifyNoInteractions(
                likeService,
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory
        );
    }

    @Test
    void deleteComment_shouldThrowWhenPostDoesNotExist() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> commentService.deleteComment(
                        COMMENT_AUTHOR_ID,
                        UserRole.USER,
                        POST_ID,
                        COMMENT_ID
                )
        );

        verify(commentRepository, never())
                .findByIdAndPost_Id(anyLong(), anyLong());
        verify(commentRepository, never()).delete(any(Comment.class));
        verifyNoInteractions(likeService, outboxRepository);
    }

    @Test
    void deleteComment_shouldThrowWhenCommentDoesNotExist() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(Optional.empty());

        assertThrows(
                CommentNotFoundException.class,
                () -> commentService.deleteComment(
                        COMMENT_AUTHOR_ID,
                        UserRole.USER,
                        POST_ID,
                        COMMENT_ID
                )
        );

        verify(commentRepository, never()).delete(any(Comment.class));
        verifyNoInteractions(likeService, outboxRepository);
    }

    // -------------------------------------------------------------------------
    // TEST DATA
    // -------------------------------------------------------------------------

    private Post createPost(Long id, Long authorId) {
        return Post.builder()
                .id(id)
                .userId(authorId)
                .content("Test post")
                .build();
    }

    private Comment createComment(
            Long id,
            Long authorId,
            Post parentPost
    ) {
        return Comment.builder()
                .id(id)
                .userId(authorId)
                .content("Test comment")
                .post(parentPost)
                .build();
    }
}
