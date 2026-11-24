package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.ProfileActivityResponse;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileActivityService {
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;

    @Transactional(readOnly = true)
    public ProfileActivityResponse getActivity(Long userId) {
        return new ProfileActivityResponse(
                postRepository.countByUserId(userId),
                commentRepository.countByUserId(userId),
                likeRepository.countByUserIdAndPostIsNotNull(userId),
                likeRepository.countByUserIdAndCommentIsNotNull(userId),
                likeRepository.countByPost_UserId(userId),
                likeRepository.countByComment_UserId(userId)
        );
    }
}
