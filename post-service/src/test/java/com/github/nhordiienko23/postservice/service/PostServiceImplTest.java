package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.dto.PostUpdateRequest;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.exception.accessdenied.PostAccessDeniedException;
import com.github.nhordiienko23.postservice.exception.notfound.PostNotFoundException;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.mapper.PostMapper;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    private static final Long POST_ID = 10L;
    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ADMIN_ID = 99L;
    private static final Long COMMENT_ID = 20L;

    private static final String PARTICIPANTS_JSON =
            "[{\"userId\":1,\"role\":\"COMMENT_AUTHOR\"}]";

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private OutboxEntryMapper outboxEntryMapper;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private LikeService likeService;

    @Mock
    private LeaderboardParticipantsJsonFactory participantsJsonFactory;

    @InjectMocks
    private PostServiceImpl postService;

    private Post post;
    private PostDTO postDTO;

    @BeforeEach
    void setUp() {
        post = createPost(POST_ID, POST_AUTHOR_ID);
        postDTO = mock(PostDTO.class);
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Test
    void createPost_shouldSavePostAndOutboxEventAndReturnDto() {
        PostCreationRequest request = mock(PostCreationRequest.class);
        OutboxEntry createdEvent = mock(OutboxEntry.class);

        when(postMapper.toEntity(POST_AUTHOR_ID, request))
                .thenReturn(post);
        when(postRepository.save(post))
                .thenReturn(post);
        when(outboxEntryMapper.toCreatedOutboxEntry(post))
                .thenReturn(createdEvent);
        when(postMapper.toDTO(post))
                .thenReturn(postDTO);

        PostDTO result = postService.createPost(POST_AUTHOR_ID, request);

        assertSame(postDTO, result);

        verify(postMapper).toEntity(POST_AUTHOR_ID, request);
        verify(postRepository).save(post);
        verify(outboxEntryMapper).toCreatedOutboxEntry(post);
        verify(outboxRepository).save(createdEvent);
        verify(postMapper).toDTO(post);
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Test
    void getPostById_shouldReturnMappedDto() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(postMapper.toDTO(post))
                .thenReturn(postDTO);

        PostDTO result = postService.getPostById(POST_ID);

        assertSame(postDTO, result);

        verify(postRepository).findById(POST_ID);
        verify(postMapper).toDTO(post);
    }

    @Test
    void getPostById_shouldThrowWhenPostDoesNotExist() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> postService.getPostById(POST_ID)
        );

        verify(postMapper, never()).toDTO(any(Post.class));
    }

    @Test
    void getAllPosts_shouldMapPageContentToDtos() {
        var pageable = PageRequest.of(0, 10);
        Page<Post> posts = new PageImpl<>(List.of(post));

        when(postRepository.findAll(pageable))
                .thenReturn(posts);
        when(postMapper.toDTO(post))
                .thenReturn(postDTO);

        Page<PostDTO> result = postService.getAllPosts(pageable);

        assertEquals(1, result.getTotalElements());
        assertSame(postDTO, result.getContent().getFirst());

        verify(postRepository).findAll(pageable);
        verify(postMapper).toDTO(post);
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Test
    void updatePost_ownerShouldUpdateContent() {
        String updatedContent = "Updated content";
        PostUpdateRequest request = mock(PostUpdateRequest.class);

        when(request.content()).thenReturn(updatedContent);
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(postMapper.toDTO(post))
                .thenReturn(postDTO);

        PostDTO result = postService.updatePost(
                POST_AUTHOR_ID,
                UserRole.USER,
                POST_ID,
                request
        );

        assertSame(postDTO, result);
        assertEquals(updatedContent, post.getContent());

        verify(postMapper).toDTO(post);
        verify(postRepository, never()).delete(any(Post.class));
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void updatePost_adminShouldBeAllowedToUpdateAnyPost() {
        String updatedContent = "Updated by admin";
        PostUpdateRequest request = mock(PostUpdateRequest.class);

        when(request.content()).thenReturn(updatedContent);
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(postMapper.toDTO(post))
                .thenReturn(postDTO);

        PostDTO result = postService.updatePost(
                ADMIN_ID,
                UserRole.ADMIN,
                POST_ID,
                request
        );

        assertSame(postDTO, result);
        assertEquals(updatedContent, post.getContent());

        verify(postMapper).toDTO(post);
    }

    @Test
    void updatePost_shouldRejectUserWhoDoesNotOwnPost() {
        PostUpdateRequest request = mock(PostUpdateRequest.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));

        assertThrows(
                PostAccessDeniedException.class,
                () -> postService.updatePost(
                        OTHER_USER_ID,
                        UserRole.USER,
                        POST_ID,
                        request
                )
        );

        verify(request, never()).content();
        verify(postMapper, never()).toDTO(any(Post.class));
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void updatePost_shouldThrowWhenPostDoesNotExist() {
        PostUpdateRequest request = mock(PostUpdateRequest.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> postService.updatePost(
                        POST_AUTHOR_ID,
                        UserRole.USER,
                        POST_ID,
                        request
                )
        );

        verify(request, never()).content();
        verify(postMapper, never()).toDTO(any(Post.class));
    }

    // -------------------------------------------------------------------------
    // DELETE: POST OWNER
    // -------------------------------------------------------------------------

    @Test
    void deletePost_ownerShouldRemoveDependentLikesWithoutScoreReversal() {
        Comment comment1 = createComment(21L, COMMENT_AUTHOR_ID, post);
        Comment comment2 = createComment(22L, POST_AUTHOR_ID, post);

        OutboxEntry postDeletionEvent = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findAllByPost_Id(POST_ID))
                .thenReturn(List.of(comment1, comment2));
        when(outboxEntryMapper.toDeletedOutboxEntry(
                post,
                POST_AUTHOR_ID,
                EventReason.USER_ACTION
        )).thenReturn(postDeletionEvent);

        postService.deletePost(
                POST_AUTHOR_ID,
                UserRole.USER,
                POST_ID
        );

        // Dependent likes are removed without score reversal.
        verify(likeService)
                .deleteLikesForPostWithoutScoreReversal(post);
        verify(likeService)
                .deleteLikesForCommentWithoutScoreReversal(comment1);
        verify(likeService)
                .deleteLikesForCommentWithoutScoreReversal(comment2);

        // No reversal events are requested.
        verify(likeService, never())
                .removeLikesForPost(
                        any(Post.class),
                        anyLong(),
                        any(EventReason.class)
                );
        verify(likeService, never())
                .removeLikesForComment(
                        any(Comment.class),
                        anyLong(),
                        any(EventReason.class)
                );

        verify(outboxEntryMapper).toDeletedOutboxEntry(
                post,
                POST_AUTHOR_ID,
                EventReason.USER_ACTION
        );
        verify(outboxRepository).save(postDeletionEvent);
        verify(postRepository).delete(post);

        verify(outboxRepository, never())
                .existsByContentIdAndEventTypeAndActionType(
                        anyLong(),
                        any(EventType.class),
                        any(ActionType.class)
                );

        verifyNoInteractions(participantsJsonFactory);
    }

    // -------------------------------------------------------------------------
    // DELETE: ADMIN MODERATION
    // -------------------------------------------------------------------------

    @Test
    void deletePost_adminShouldReversePostCommentAndLikeScores() {
        Comment comment = createComment(
                COMMENT_ID,
                COMMENT_AUTHOR_ID,
                post
        );

        OutboxEntry postDeletionEvent = mock(OutboxEntry.class);
        OutboxEntry commentDeletionEvent = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findAllByPost_Id(POST_ID))
                .thenReturn(List.of(comment));

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
                EventReason.PARENT_DELETED
        )).thenReturn(commentDeletionEvent);

        when(outboxEntryMapper.toDeletedOutboxEntry(
                post,
                ADMIN_ID,
                EventReason.MODERATION
        )).thenReturn(postDeletionEvent);

        postService.deletePost(
                ADMIN_ID,
                UserRole.ADMIN,
                POST_ID
        );

        // Reverse scores for likes on the post and on its comments.
        verify(likeService).removeLikesForPost(
                post,
                ADMIN_ID,
                EventReason.PARENT_DELETED
        );
        verify(likeService).removeLikesForComment(
                comment,
                ADMIN_ID,
                EventReason.PARENT_DELETED
        );

        // Do not use the no-score-reversal path for moderation.
        verify(likeService, never())
                .deleteLikesForPostWithoutScoreReversal(any(Post.class));
        verify(likeService, never())
                .deleteLikesForCommentWithoutScoreReversal(
                        any(Comment.class)
                );

        verify(outboxRepository).save(commentDeletionEvent);
        verify(outboxRepository).save(postDeletionEvent);
        verify(postRepository).delete(post);
    }

    @Test
    void deletePost_adminShouldNotReverseUnscoredComment() {
        Comment comment = createComment(
                COMMENT_ID,
                COMMENT_AUTHOR_ID,
                post
        );

        OutboxEntry postDeletionEvent = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findAllByPost_Id(POST_ID))
                .thenReturn(List.of(comment));

        when(outboxRepository.existsByContentIdAndEventTypeAndActionType(
                COMMENT_ID,
                EventType.COMMENT,
                ActionType.CREATED
        )).thenReturn(false);

        when(outboxEntryMapper.toDeletedOutboxEntry(
                post,
                ADMIN_ID,
                EventReason.MODERATION
        )).thenReturn(postDeletionEvent);

        postService.deletePost(
                ADMIN_ID,
                UserRole.ADMIN,
                POST_ID
        );

        // Likes are still physically removed.
        verify(likeService).removeLikesForPost(
                post,
                ADMIN_ID,
                EventReason.PARENT_DELETED
        );
        verify(likeService).removeLikesForComment(
                comment,
                ADMIN_ID,
                EventReason.PARENT_DELETED
        );

        // Do not create a reversal event for an unscored comment.
        verify(outboxEntryMapper, never())
                .toDeletedCommentOutboxEntry(
                        any(Comment.class),
                        anyLong(),
                        anyString(),
                        any(EventReason.class)
                );

        verify(outboxRepository).save(postDeletionEvent);
        verify(outboxRepository, times(1)).save(any(OutboxEntry.class));
        verify(postRepository).delete(post);
    }

    @Test
    void deletePost_adminShouldSkipScoreReversalForSelfComment() {
        Comment selfComment = createComment(
                COMMENT_ID,
                POST_AUTHOR_ID,
                post
        );

        OutboxEntry postDeletionEvent = mock(OutboxEntry.class);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
        when(commentRepository.findAllByPost_Id(POST_ID))
                .thenReturn(List.of(selfComment));

        when(outboxEntryMapper.toDeletedOutboxEntry(
                post,
                ADMIN_ID,
                EventReason.MODERATION
        )).thenReturn(postDeletionEvent);

        postService.deletePost(
                ADMIN_ID,
                UserRole.ADMIN,
                POST_ID
        );

        // Remove any dependent likes, but skip the comment score event.
        verify(likeService).removeLikesForComment(
                selfComment,
                ADMIN_ID,
                EventReason.PARENT_DELETED
        );

        verify(outboxRepository, never())
                .existsByContentIdAndEventTypeAndActionType(
                        COMMENT_ID,
                        EventType.COMMENT,
                        ActionType.CREATED
                );

        verify(outboxEntryMapper, never())
                .toDeletedCommentOutboxEntry(
                        any(Comment.class),
                        anyLong(),
                        anyString(),
                        any(EventReason.class)
                );

        verify(outboxRepository).save(postDeletionEvent);
        verify(outboxRepository, times(1)).save(any(OutboxEntry.class));
        verify(postRepository).delete(post);
    }

    // -------------------------------------------------------------------------
    // DELETE: ACCESS CONTROL AND NOT FOUND
    // -------------------------------------------------------------------------

    @Test
    void deletePost_shouldRejectUserWhoDoesNotOwnPost() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));

        assertThrows(
                PostAccessDeniedException.class,
                () -> postService.deletePost(
                        OTHER_USER_ID,
                        UserRole.USER,
                        POST_ID
                )
        );

        verify(postRepository, never()).delete(any(Post.class));

        verifyNoInteractions(
                commentRepository,
                outboxEntryMapper,
                outboxRepository,
                likeService,
                participantsJsonFactory
        );
    }

    @Test
    void deletePost_shouldThrowWhenPostDoesNotExist() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> postService.deletePost(
                        POST_AUTHOR_ID,
                        UserRole.USER,
                        POST_ID
                )
        );

        verify(postRepository, never()).delete(any(Post.class));

        verifyNoInteractions(
                commentRepository,
                outboxEntryMapper,
                outboxRepository,
                likeService,
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
}