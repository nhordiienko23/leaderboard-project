package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.postservice.dto.LikeDTO;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Post;

public interface LikeService {

    LikeDTO likePost(Long userId, Long postId);

    void unlikePost(Long userId, Long postId);

    LikeDTO likeComment(Long userId, Long postId, Long commentId);

    void unlikeComment(Long userId, Long postId, Long commentId);

    void removeLikesForPost(Post post, Long actorId, EventReason reason);

    void removeLikesForComment(Comment comment, Long actorId, EventReason reason);

    void deleteLikesForPostWithoutScoreReversal(Post post);

    void deleteLikesForCommentWithoutScoreReversal(Comment comment);

    default void removeLikesForPost(Post post, Long actorId) {
        removeLikesForPost(post, actorId, EventReason.USER_ACTION);
    }

    default void removeLikesForComment(Comment comment, Long actorId) {
        removeLikesForComment(comment, actorId, EventReason.USER_ACTION);
    }
}