package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id
    ) {
        postService.deletePost(userId, id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

    }
    @GetMapping("/{id}")
    public ResponseEntity<PostDTO> getPost(
            @PathVariable Long id
    ){
        return ResponseEntity.status(HttpStatus.OK)
                .body(postService.getPostById(id));
    }
}
