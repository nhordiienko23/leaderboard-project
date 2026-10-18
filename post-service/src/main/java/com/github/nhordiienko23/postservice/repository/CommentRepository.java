package com.github.nhordiienko23.postservice.repository;

import com.github.nhordiienko23.postservice.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findAllByPost_Id(Long postId, Pageable pageable);

    List<Comment> findAllByPost_Id(Long postId);

    Optional<Comment> findByIdAndPost_Id(Long commentId, Long postId);
}