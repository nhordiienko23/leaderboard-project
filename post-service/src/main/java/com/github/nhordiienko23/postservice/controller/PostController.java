package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.dto.PostUpdateRequest;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostDTO> createPost(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PostCreationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createPost(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader("X-User-Role") UserRole role,
            @PathVariable Long id
    ) {
        postService.deletePost(actorId, role, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDTO> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    @GetMapping
    public ResponseEntity<Page<PostDTO>> getAllPosts(
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(postService.getAllPosts(pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostDTO> updatePost(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader("X-User-Role") UserRole role,
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        return ResponseEntity.ok(
                postService.updatePost(actorId, role, id, request)
        );
    }
}
