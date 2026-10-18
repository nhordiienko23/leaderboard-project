package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.dto.LikeDTO;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Like;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.mapper.LikeMapper;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    private static final Long POST_ID = 10L;
    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_ID = 20L;
    private static final Long COMMENT_AUTHOR_ID = 1L;
    private static final Long LIKER_ID = 2L;
    private static final Long ADMIN_ID = 99L;

    private static final String PARTICIPANTS_JSON =
            "[{\"userId\":2,\"role\":\"LIKER\"}]";

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxEntryMapper outboxEntryMapper;

    @Mock
    private LikeMapper likeMapper;

    @Mock
    private LeaderboardParticipantsJsonFactory participantsJsonFactory;

    @InjectMocks
    private LikeServiceImpl likeService;

    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        post = createPost(POST_ID, POST_AUTHOR_ID);
        comment = createComment(COMMENT_ID, COMMENT_AUTHOR_ID, post);
    }

    // -------------------------------------------------------------------------
    // LIKE POST
    // -------------------------------------------------------------------------

    @Test
    void likePost_shouldSaveLikeAndCreateScoringEvent() {
        Like savedLike = createPostLike(101L, LIKER_ID, post);
        LikeDTO expectedDto = mock(LikeDTO.class);
        OutboxEntry event = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(likeRepository.existsByUserIdAndPost_Id(LIKER_ID, POST_ID))
                .thenReturn(false);
        when(likeRepository.save(any(Like.class)))
                .thenReturn(savedLike);
        when(participantsJsonFactory.forLike(
                LIKER_ID,
                POST_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);
        when(outboxEntryMapper.toCreatedLikeOutboxEntry(
                savedLike,
                POST_AUTHOR_ID,
                PARTICIPANTS_JSON
        )).thenReturn(event);
        when(likeMapper.toDTO(savedLike)).thenReturn(expectedDto);

        LikeDTO result = likeService.likePost(LIKER_ID, POST_ID);

        assertSame(expectedDto, result);

        verify(likeRepository).save(any(Like.class));
        verify(participantsJsonFactory).forLike(
                LIKER_ID,
                POST_AUTHOR_ID
        );
        verify(outboxRepository).save(event);
        verify(likeMapper).toDTO(savedLike);
    }

    @Test
    void likePost_shouldNotCreateScoringEventForSelfLike() {
        Like selfLike = createPostLike(102L, POST_AUTHOR_ID, post);
        LikeDTO expectedDto = mock(LikeDTO.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(likeRepository.existsByUserIdAndPost_Id(
                POST_AUTHOR_ID,
                POST_ID
        )).thenReturn(false);
        when(likeRepository.save(any(Like.class)))
                .thenReturn(selfLike);
        when(likeMapper.toDTO(selfLike)).thenReturn(expectedDto);

        LikeDTO result = likeService.likePost(POST_AUTHOR_ID, POST_ID);

        assertSame(expectedDto, result);

        verify(likeRepository).save(any(Like.class));
        verifyNoInteractions(
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory
        );
    }

    @Test
    void likePost_shouldRejectDuplicateLike() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(likeRepository.existsByUserIdAndPost_Id(LIKER_ID, POST_ID))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> likeService.likePost(LIKER_ID, POST_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(likeRepository, never()).save(any(Like.class));
        verifyNoInteractions(
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory,
                likeMapper
        );
    }

    @Test
    void likePost_shouldThrowWhenPostDoesNotExist() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> likeService.likePost(LIKER_ID, POST_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(
                likeRepository,
                commentRepository,
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory,
                likeMapper
        );
    }

    // -------------------------------------------------------------------------
    // UNLIKE POST
    // -------------------------------------------------------------------------

    @Test
    void unlikePost_shouldCreateReversalEventAndDeleteLike() {
        Like like = createPostLike(101L, LIKER_ID, post);
        OutboxEntry event = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(likeRepository.findByUserIdAndPost_Id(LIKER_ID, POST_ID))
                .thenReturn(Optional.of(like));
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        )).thenReturn(true);
        when(participantsJsonFactory.forLike(
                LIKER_ID,
                POST_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);
        when(outboxEntryMapper.toDeletedLikeOutboxEntry(
                like,
                LIKER_ID,
                POST_AUTHOR_ID,
                PARTICIPANTS_JSON,
                EventReason.USER_ACTION
        )).thenReturn(event);

        likeService.unlikePost(LIKER_ID, POST_ID);

        verify(outboxRepository).save(event);
        verify(likeRepository).delete(like);
    }

    @Test
    void unlikePost_shouldDeleteLikeWithoutReversalWhenCreationWasNotScored() {
        Like like = createPostLike(101L, LIKER_ID, post);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(likeRepository.findByUserIdAndPost_Id(LIKER_ID, POST_ID))
                .thenReturn(Optional.of(like));
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        )).thenReturn(false);

        likeService.unlikePost(LIKER_ID, POST_ID);

        verify(likeRepository).delete(like);
        verify(outboxRepository, never()).save(any(OutboxEntry.class));
        verifyNoInteractions(outboxEntryMapper, participantsJsonFactory);
    }

    @Test
    void unlikePost_selfLikeShouldNotCreateReversalEvent() {
        Like like = createPostLike(102L, POST_AUTHOR_ID, post);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(likeRepository.findByUserIdAndPost_Id(
                POST_AUTHOR_ID,
                POST_ID
        )).thenReturn(Optional.of(like));

        likeService.unlikePost(POST_AUTHOR_ID, POST_ID);

        verify(likeRepository).delete(like);
        verify(outboxRepository, never())
                .existsByContentIdAndEventTypeAndActionType(
                        anyLong(),
                        any(EventType.class),
                        any(ActionType.class)
                );
        verify(outboxRepository, never()).save(any(OutboxEntry.class));
    }

    @Test
    void unlikePost_shouldThrowWhenLikeDoesNotExist() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(likeRepository.findByUserIdAndPost_Id(LIKER_ID, POST_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> likeService.unlikePost(LIKER_ID, POST_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verify(likeRepository, never()).delete(any(Like.class));
        verifyNoInteractions(outboxEntryMapper, participantsJsonFactory);
    }

    // -------------------------------------------------------------------------
    // LIKE COMMENT
    // -------------------------------------------------------------------------

    @Test
    void likeComment_shouldSaveLikeAndCreateScoringEvent() {
        Like savedLike = createCommentLike(103L, LIKER_ID, comment);
        LikeDTO expectedDto = mock(LikeDTO.class);
        OutboxEntry event = mock(OutboxEntry.class);

        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID))
                .thenReturn(Optional.of(comment));
        when(likeRepository.existsByUserIdAndComment_Id(
                LIKER_ID,
                COMMENT_ID
        )).thenReturn(false);
        when(likeRepository.save(any(Like.class)))
                .thenReturn(savedLike);
        when(participantsJsonFactory.forLike(
                LIKER_ID,
                COMMENT_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);
        when(outboxEntryMapper.toCreatedLikeOutboxEntry(
                savedLike,
                COMMENT_AUTHOR_ID,
                PARTICIPANTS_JSON
        )).thenReturn(event);
        when(likeMapper.toDTO(savedLike)).thenReturn(expectedDto);

        LikeDTO result = likeService.likeComment(
                LIKER_ID,
                POST_ID,
                COMMENT_ID
        );

        assertSame(expectedDto, result);

        verify(likeRepository).save(any(Like.class));
        verify(outboxRepository).save(event);
        verify(participantsJsonFactory).forLike(
                LIKER_ID,
                COMMENT_AUTHOR_ID
        );
        verify(likeMapper).toDTO(savedLike);
    }

    @Test
    void likeComment_shouldNotCreateScoringEventForSelfLike() {
        Like selfLike = createCommentLike(
                104L,
                COMMENT_AUTHOR_ID,
                comment
        );
        LikeDTO expectedDto = mock(LikeDTO.class);

        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID))
                .thenReturn(Optional.of(comment));
        when(likeRepository.existsByUserIdAndComment_Id(
                COMMENT_AUTHOR_ID,
                COMMENT_ID
        )).thenReturn(false);
        when(likeRepository.save(any(Like.class)))
                .thenReturn(selfLike);
        when(likeMapper.toDTO(selfLike)).thenReturn(expectedDto);

        LikeDTO result = likeService.likeComment(
                COMMENT_AUTHOR_ID,
                POST_ID,
                COMMENT_ID
        );

        assertSame(expectedDto, result);

        verify(likeRepository).save(any(Like.class));
        verifyNoInteractions(
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory
        );
    }

    @Test
    void likeComment_shouldRejectDuplicateLike() {
        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID))
                .thenReturn(Optional.of(comment));
        when(likeRepository.existsByUserIdAndComment_Id(
                LIKER_ID,
                COMMENT_ID
        )).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> likeService.likeComment(
                        LIKER_ID,
                        POST_ID,
                        COMMENT_ID
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(likeRepository, never()).save(any(Like.class));
        verifyNoInteractions(
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory,
                likeMapper
        );
    }

    @Test
    void likeComment_shouldThrowWhenCommentDoesNotExist() {
        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> likeService.likeComment(
                        LIKER_ID,
                        POST_ID,
                        COMMENT_ID
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(
                likeRepository,
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory,
                likeMapper
        );
    }

    // -------------------------------------------------------------------------
    // UNLIKE COMMENT
    // -------------------------------------------------------------------------

    @Test
    void unlikeComment_shouldCreateReversalEventAndDeleteLike() {
        Like like = createCommentLike(103L, LIKER_ID, comment);
        OutboxEntry event = mock(OutboxEntry.class);

        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID))
                .thenReturn(Optional.of(comment));
        when(likeRepository.findByUserIdAndComment_Id(
                LIKER_ID,
                COMMENT_ID
        )).thenReturn(Optional.of(like));
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        )).thenReturn(true);
        when(participantsJsonFactory.forLike(
                LIKER_ID,
                COMMENT_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);
        when(outboxEntryMapper.toDeletedLikeOutboxEntry(
                like,
                LIKER_ID,
                COMMENT_AUTHOR_ID,
                PARTICIPANTS_JSON,
                EventReason.USER_ACTION
        )).thenReturn(event);

        likeService.unlikeComment(LIKER_ID, POST_ID, COMMENT_ID);

        verify(outboxRepository).save(event);
        verify(likeRepository).delete(like);
    }

    @Test
    void unlikeComment_shouldDeleteLikeWithoutReversalWhenCreationWasNotScored() {
        Like like = createCommentLike(103L, LIKER_ID, comment);

        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID))
                .thenReturn(Optional.of(comment));
        when(likeRepository.findByUserIdAndComment_Id(
                LIKER_ID,
                COMMENT_ID
        )).thenReturn(Optional.of(like));
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        )).thenReturn(false);

        likeService.unlikeComment(LIKER_ID, POST_ID, COMMENT_ID);

        verify(likeRepository).delete(like);
        verify(outboxRepository, never()).save(any(OutboxEntry.class));
        verifyNoInteractions(outboxEntryMapper, participantsJsonFactory);
    }

    @Test
    void unlikeComment_shouldThrowWhenLikeDoesNotExist() {
        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID))
                .thenReturn(Optional.of(comment));
        when(likeRepository.findByUserIdAndComment_Id(
                LIKER_ID,
                COMMENT_ID
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> likeService.unlikeComment(
                        LIKER_ID,
                        POST_ID,
                        COMMENT_ID
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verify(likeRepository, never()).delete(any(Like.class));
        verifyNoInteractions(outboxEntryMapper, participantsJsonFactory);
    }

    // -------------------------------------------------------------------------
    // REMOVE LIKES DURING MODERATION
    // -------------------------------------------------------------------------

    @Test
    void removeLikesForPost_shouldReverseScoredLikesAndDeleteAllLikes() {
        Like scoredLike = createPostLike(101L, LIKER_ID, post);
        Like selfLike = createPostLike(102L, POST_AUTHOR_ID, post);
        OutboxEntry event = mock(OutboxEntry.class);

        when(likeRepository.findAllByPost_Id(POST_ID))
                .thenReturn(List.of(scoredLike, selfLike));
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                scoredLike.getId(),
                EventType.LIKE,
                ActionType.CREATED
        )).thenReturn(true);
        when(participantsJsonFactory.forLike(
                LIKER_ID,
                POST_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);
        when(outboxEntryMapper.toDeletedLikeOutboxEntry(
                scoredLike,
                ADMIN_ID,
                POST_AUTHOR_ID,
                PARTICIPANTS_JSON,
                EventReason.PARENT_DELETED
        )).thenReturn(event);

        likeService.removeLikesForPost(
                post,
                ADMIN_ID,
                EventReason.PARENT_DELETED
        );

        verify(outboxRepository).save(event);
        verify(likeRepository).delete(scoredLike);
        verify(likeRepository).delete(selfLike);

        // Self-likes were never scored, so no reversal is created for them.
        verify(outboxRepository, times(1))
                .existsByContentIdAndEventTypeAndActionType(
                        anyLong(),
                        eq(EventType.LIKE),
                        eq(ActionType.CREATED)
                );
    }

    @Test
    void removeLikesForComment_shouldReverseScoredLikesAndDeleteThem() {
        Like like = createCommentLike(103L, LIKER_ID, comment);
        OutboxEntry event = mock(OutboxEntry.class);

        when(likeRepository.findAllByComment_Id(COMMENT_ID))
                .thenReturn(List.of(like));
        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                like.getId(),
                EventType.LIKE,
                ActionType.CREATED
        )).thenReturn(true);
        when(participantsJsonFactory.forLike(
                LIKER_ID,
                COMMENT_AUTHOR_ID
        )).thenReturn(PARTICIPANTS_JSON);
        when(outboxEntryMapper.toDeletedLikeOutboxEntry(
                like,
                ADMIN_ID,
                COMMENT_AUTHOR_ID,
                PARTICIPANTS_JSON,
                EventReason.PARENT_DELETED
        )).thenReturn(event);

        likeService.removeLikesForComment(
                comment,
                ADMIN_ID,
                EventReason.PARENT_DELETED
        );

        verify(outboxRepository).save(event);
        verify(likeRepository).delete(like);
    }

    // -------------------------------------------------------------------------
    // PHYSICAL CLEANUP WITHOUT SCORE REVERSAL
    // -------------------------------------------------------------------------

    @Test
    void deleteLikesForPostWithoutScoreReversal_shouldDeleteLikesWithoutEvents() {
        List<Like> likes = List.of(
                createPostLike(101L, LIKER_ID, post),
                createPostLike(102L, POST_AUTHOR_ID, post)
        );

        when(likeRepository.findAllByPost_Id(POST_ID))
                .thenReturn(likes);

        likeService.deleteLikesForPostWithoutScoreReversal(post);

        verify(likeRepository).deleteAll(likes);
        verifyNoInteractions(
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory
        );
    }

    @Test
    void deleteLikesForCommentWithoutScoreReversal_shouldDeleteLikesWithoutEvents() {
        List<Like> likes = List.of(
                createCommentLike(103L, LIKER_ID, comment)
        );

        when(likeRepository.findAllByComment_Id(COMMENT_ID))
                .thenReturn(likes);

        likeService.deleteLikesForCommentWithoutScoreReversal(comment);

        verify(likeRepository).deleteAll(likes);
        verifyNoInteractions(
                outboxRepository,
                outboxEntryMapper,
                participantsJsonFactory
        );
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

    private Like createPostLike(
            Long id,
            Long userId,
            Post targetPost
    ) {
        return Like.builder()
                .id(id)
                .userId(userId)
                .post(targetPost)
                .build();
    }

    private Like createCommentLike(
            Long id,
            Long userId,
            Comment targetComment
    ) {
        return Like.builder()
                .id(id)
                .userId(userId)
                .comment(targetComment)
                .build();
    }
}
