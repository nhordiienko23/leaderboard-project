package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.dto.PostUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface PostService {
    PostDTO createPost(Long userId, PostCreationRequest request);
    void deletePost(Long userId, Long postId);
    PostDTO getPostById(Long id);
    Page<PostDTO> getAllPosts(Pageable pageable);
    PostDTO updatePost(Long userId, PostUpdateRequest request);
}
