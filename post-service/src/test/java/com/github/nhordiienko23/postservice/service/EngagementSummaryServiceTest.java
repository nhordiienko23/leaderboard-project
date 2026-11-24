package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.CommentEngagementResponse;
import com.github.nhordiienko23.postservice.dto.PostEngagementResponse;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.exception.notfound.CommentNotFoundException;
import com.github.nhordiienko23.postservice.exception.notfound.PostNotFoundException;
import com.github.nhordiienko23.postservice.mapper.CommentMapper;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.mapper.PostMapper;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Engagement Summary Service Tests")
class EngagementSummaryServiceTest {

    private static final Long POST_ID = 10L;
    private static final Long COMMENT_ID = 20L;
    private static final Long VIEWER_ID = 30L;

    @Mock
    private PostMapper postMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxEntryMapper outboxEntryMapper;

    @Mock
    private LikeService likeService;

    @Mock
    private LeaderboardParticipantsJsonFactory participantsJsonFactory;

    @InjectMocks
    private PostServiceImpl postService;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    @DisplayName("Post summary returns zero counts and null viewer state anonymously")
    void postSummaryShouldReturnZeroCountsForAnonymousViewer() {
        when(postRepository.existsById(POST_ID)).thenReturn(true);
        when(likeRepository.countByPost_Id(POST_ID)).thenReturn(0L);
        when(commentRepository.countByPost_Id(POST_ID)).thenReturn(0L);

        PostEngagementResponse response =
                postService.getEngagement(POST_ID, null);

        assertEquals(0L, response.likeCount());
        assertEquals(0L, response.commentCount());
        assertNull(response.likedByMe());
        verify(likeRepository, never())
                .existsByUserIdAndPost_Id(VIEWER_ID, POST_ID);
        verify(likeRepository, never()).findAllByPost_Id(POST_ID);
        verify(commentRepository, never()).findAllByPost_Id(POST_ID);
    }

    @Test
    @DisplayName("Post summary uses count queries and reports an existing like")
    void postSummaryShouldReturnNonzeroCountsAndExistingLike() {
        when(postRepository.existsById(POST_ID)).thenReturn(true);
        when(likeRepository.countByPost_Id(POST_ID)).thenReturn(3L);
        when(commentRepository.countByPost_Id(POST_ID)).thenReturn(2L);
        when(likeRepository.existsByUserIdAndPost_Id(
                VIEWER_ID,
                POST_ID
        )).thenReturn(true);

        PostEngagementResponse response =
                postService.getEngagement(POST_ID, VIEWER_ID);

        assertEquals(3L, response.likeCount());
        assertEquals(2L, response.commentCount());
        assertTrue(response.likedByMe());
        verify(likeRepository).countByPost_Id(POST_ID);
        verify(commentRepository).countByPost_Id(POST_ID);
    }

    @Test
    @DisplayName("Post summary reports false when the viewer has not liked it")
    void postSummaryShouldReturnFalseWithoutExistingLike() {
        when(postRepository.existsById(POST_ID)).thenReturn(true);
        when(likeRepository.existsByUserIdAndPost_Id(
                VIEWER_ID,
                POST_ID
        )).thenReturn(false);

        PostEngagementResponse response =
                postService.getEngagement(POST_ID, VIEWER_ID);

        assertFalse(response.likedByMe());
    }

    @Test
    @DisplayName("Post summary rejects a missing post before counting")
    void postSummaryShouldRejectMissingPost() {
        when(postRepository.existsById(POST_ID)).thenReturn(false);

        assertThrows(
                PostNotFoundException.class,
                () -> postService.getEngagement(POST_ID, VIEWER_ID)
        );

        verify(likeRepository, never()).countByPost_Id(POST_ID);
        verify(commentRepository, never()).countByPost_Id(POST_ID);
    }

    @Test
    @DisplayName("Comment summary returns zero likes and null viewer state anonymously")
    void commentSummaryShouldReturnZeroLikesForAnonymousViewer() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(mock(Post.class)));
        when(commentRepository.existsByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(true);
        when(likeRepository.countByComment_Id(COMMENT_ID)).thenReturn(0L);

        CommentEngagementResponse response =
                commentService.getEngagement(POST_ID, COMMENT_ID, null);

        assertEquals(0L, response.likeCount());
        assertNull(response.likedByMe());
        verify(likeRepository, never())
                .existsByUserIdAndComment_Id(VIEWER_ID, COMMENT_ID);
        verify(likeRepository, never())
                .findAllByComment_Id(COMMENT_ID);
    }

    @Test
    @DisplayName("Comment summary uses a count query and reports an existing like")
    void commentSummaryShouldReturnNonzeroLikesAndExistingLike() {
        mockExistingPost();
        when(commentRepository.existsByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(true);
        when(likeRepository.countByComment_Id(COMMENT_ID)).thenReturn(4L);
        when(likeRepository.existsByUserIdAndComment_Id(
                VIEWER_ID,
                COMMENT_ID
        )).thenReturn(true);

        CommentEngagementResponse response =
                commentService.getEngagement(
                        POST_ID,
                        COMMENT_ID,
                        VIEWER_ID
                );

        assertEquals(4L, response.likeCount());
        assertTrue(response.likedByMe());
        verify(likeRepository).countByComment_Id(COMMENT_ID);
    }

    @Test
    @DisplayName("Comment summary reports false when the viewer has not liked it")
    void commentSummaryShouldReturnFalseWithoutExistingLike() {
        mockExistingPost();
        when(commentRepository.existsByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(true);
        when(likeRepository.existsByUserIdAndComment_Id(
                VIEWER_ID,
                COMMENT_ID
        )).thenReturn(false);

        CommentEngagementResponse response =
                commentService.getEngagement(
                        POST_ID,
                        COMMENT_ID,
                        VIEWER_ID
                );

        assertFalse(response.likedByMe());
    }

    @Test
    @DisplayName("Comment summary rejects a missing post")
    void commentSummaryShouldRejectMissingPost() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PostNotFoundException.class,
                () -> commentService.getEngagement(
                        POST_ID,
                        COMMENT_ID,
                        VIEWER_ID
                )
        );

        verify(commentRepository, never())
                .existsByIdAndPost_Id(COMMENT_ID, POST_ID);
    }

    @Test
    @DisplayName("Comment summary rejects a missing or differently parented comment")
    void commentSummaryShouldRejectMissingCommentForPost() {
        mockExistingPost();
        when(commentRepository.existsByIdAndPost_Id(
                COMMENT_ID,
                POST_ID
        )).thenReturn(false);

        assertThrows(
                CommentNotFoundException.class,
                () -> commentService.getEngagement(
                        POST_ID,
                        COMMENT_ID,
                        VIEWER_ID
                )
        );

        verify(likeRepository, never()).countByComment_Id(COMMENT_ID);
    }

    private void mockExistingPost() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(mock(Post.class)));
    }
}
