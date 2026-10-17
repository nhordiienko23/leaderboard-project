package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.dto.PostUpdateRequest;
import com.github.nhordiienko23.postservice.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostService {

    PostDTO createPost(Long userId, PostCreationRequest request);

    void deletePost(Long actorId, UserRole role, Long postId);

    PostDTO getPostById(Long id);

    Page<PostDTO> getAllPosts(Pageable pageable);

    PostDTO updatePost(
            Long actorId,
            UserRole role,
            Long postId,
            PostUpdateRequest request
    );
}