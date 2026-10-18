package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.CommentCreationRequest;
import com.github.nhordiienko23.postservice.dto.CommentDTO;
import com.github.nhordiienko23.postservice.dto.CommentUpdateRequest;
import com.github.nhordiienko23.postservice.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentService {

    CommentDTO createComment(
            Long userId,
            Long postId,
            CommentCreationRequest request
    );

    Page<CommentDTO> getComments(
            Long postId,
            Pageable pageable
    );

    CommentDTO getComment(
            Long postId,
            Long commentId
    );

    CommentDTO updateComment(
            Long actorId,
            UserRole role,
            Long postId,
            Long commentId,
            CommentUpdateRequest request
    );

    void deleteComment(
            Long actorId,
            UserRole role,
            Long postId,
            Long commentId
    );
}