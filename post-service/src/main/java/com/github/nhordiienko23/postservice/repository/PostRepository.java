package com.github.nhordiienko23.postservice.repository;

import com.github.nhordiienko23.postservice.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post,Long> {
    Optional<Post> findByUserIdAndId(Long userId,Long postId);
}
