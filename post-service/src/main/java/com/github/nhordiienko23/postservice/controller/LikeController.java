package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.postservice.dto.LikeDTO;
import com.github.nhordiienko23.postservice.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/posts/{postId}/likes")
    public ResponseEntity<LikeDTO> likePost(
            @PathVariable Long postId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(likeService.likePost(userId, postId));
    }

    @DeleteMapping("/posts/{postId}/likes")
    public ResponseEntity<Void> unlikePost(
            @PathVariable Long postId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        likeService.unlikePost(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/comments/{commentId}/likes")
    public ResponseEntity<LikeDTO> likeComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(likeService.likeComment(userId, postId, commentId));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}/likes")
    public ResponseEntity<Void> unlikeComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        likeService.unlikeComment(userId, postId, commentId);
        return ResponseEntity.noContent().build();
    }
}