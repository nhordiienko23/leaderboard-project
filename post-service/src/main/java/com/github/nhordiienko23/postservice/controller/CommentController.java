package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.postservice.dto.CommentCreationRequest;
import com.github.nhordiienko23.postservice.dto.CommentDTO;
import com.github.nhordiienko23.postservice.dto.CommentUpdateRequest;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentDTO> createComment(
            @PathVariable Long postId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CommentCreationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(
                        userId,
                        postId,
                        request
                ));
    }

    @GetMapping
    public ResponseEntity<Page<CommentDTO>> getComments(
            @PathVariable Long postId,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(
                commentService.getComments(postId, pageable)
        );
    }

    @GetMapping("/{commentId}")
    public ResponseEntity<CommentDTO> getComment(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        return ResponseEntity.ok(
                commentService.getComment(postId, commentId)
        );
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentDTO> updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader("X-User-Role") UserRole role,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        return ResponseEntity.ok(
                commentService.updateComment(
                        actorId,
                        role,
                        postId,
                        commentId,
                        request
                )
        );
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader("X-User-Role") UserRole role
    ) {
        commentService.deleteComment(
                actorId,
                role,
                postId,
                commentId
        );

        return ResponseEntity.noContent().build();
    }
}