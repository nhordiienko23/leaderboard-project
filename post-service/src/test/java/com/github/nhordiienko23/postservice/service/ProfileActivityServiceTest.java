package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.ProfileActivityResponse;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ProfileActivityServiceTest {
    @Test
    void aggregatesEachIndependentCountReadOnly() throws Exception {
        PostRepository posts = mock(PostRepository.class);
        CommentRepository comments = mock(CommentRepository.class);
        LikeRepository likes = mock(LikeRepository.class);
        when(posts.countByUserId(7L)).thenReturn(1L);
        when(comments.countByUserId(7L)).thenReturn(2L);
        when(likes.countByUserIdAndPostIsNotNull(7L)).thenReturn(3L);
        when(likes.countByUserIdAndCommentIsNotNull(7L)).thenReturn(4L);
        when(likes.countByPost_UserId(7L)).thenReturn(5L);
        when(likes.countByComment_UserId(7L)).thenReturn(6L);

        assertEquals(new ProfileActivityResponse(1, 2, 3, 4, 5, 6),
                new ProfileActivityService(posts, comments, likes).getActivity(7L));
        assertTrue(ProfileActivityService.class.getMethod("getActivity", Long.class)
                .getAnnotation(Transactional.class).readOnly());
        verify(posts).countByUserId(7L);
        verify(comments).countByUserId(7L);
        verify(likes).countByUserIdAndPostIsNotNull(7L);
        verify(likes).countByUserIdAndCommentIsNotNull(7L);
        verify(likes).countByPost_UserId(7L);
        verify(likes).countByComment_UserId(7L);
        verifyNoMoreInteractions(posts, comments, likes);
    }
}
