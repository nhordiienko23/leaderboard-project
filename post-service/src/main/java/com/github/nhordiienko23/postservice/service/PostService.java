package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;


public interface PostService {
    PostDTO createPost(Long userId, PostCreationRequest request);
    void deletePost(Long userId, Long postId);
    PostDTO getPostById(Long id);
}
