package com.github.nhordiienko23.postservice.repository;

import com.github.nhordiienko23.postservice.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserIdAndPost_Id(Long userId, Long postId);

    boolean existsByUserIdAndComment_Id(Long userId, Long commentId);

    Optional<Like> findByUserIdAndPost_Id(Long userId, Long postId);

    Optional<Like> findByUserIdAndComment_Id(
            Long userId,
            Long commentId
    );

    List<Like> findAllByPost_Id(Long postId);

    List<Like> findAllByComment_Id(Long commentId);
}