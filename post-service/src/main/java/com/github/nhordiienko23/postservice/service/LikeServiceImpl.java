package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.dto.LikeDTO;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Like;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.mapper.LikeMapper;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LikeServiceImpl implements LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final OutboxRepository outboxRepository;
    private final OutboxEntryMapper outboxEntryMapper;
    private final LikeMapper likeMapper;
    private final LeaderboardParticipantsJsonFactory participantsJsonFactory;

    @Override
    public LikeDTO likePost(Long userId, Long postId) {
        Post post = findPost(postId);

        if (likeRepository.existsByUserIdAndPost_Id(userId, postId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You have already liked this post"
            );
        }

        Like like = Like.builder()
                .userId(userId)
                .post(post)
                .build();

        Like savedLike = likeRepository.save(like);

        if (isNotOwnContent(userId, post.getUserId())) {
            enqueueCreatedEvent(savedLike, post.getUserId());
        } else {
            log.info(
                    "Self-like saved without leaderboard points: likeId={}, postId={}, userId={}",
                    savedLike.getId(), postId, userId
            );
        }

        log.info(
                "Post liked: likeId={}, postId={}, likerId={}",
                savedLike.getId(), postId, userId
        );

        return likeMapper.toDTO(savedLike);
    }

    @Override
    public void unlikePost(Long userId, Long postId) {
        findPost(postId);

        Like like = likeRepository
                .findByUserIdAndPost_Id(userId, postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Like not found"
                ));

        enqueueDeletedEvent(like, userId, EventReason.USER_ACTION);
        likeRepository.delete(like);

        log.info(
                "Post unliked: likeId={}, postId={}, userId={}",
                like.getId(), postId, userId
        );
    }

    @Override
    public LikeDTO likeComment(Long userId, Long postId, Long commentId) {
        Comment comment = findComment(postId, commentId);

        if (likeRepository.existsByUserIdAndComment_Id(userId, commentId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You have already liked this comment"
            );
        }

        Like like = Like.builder()
                .userId(userId)
                .comment(comment)
                .build();

        Like savedLike = likeRepository.save(like);

        if (isNotOwnContent(userId, comment.getUserId())) {
            enqueueCreatedEvent(savedLike, comment.getUserId());
        } else {
            log.info(
                    "Self-like saved without leaderboard points: likeId={}, commentId={}, userId={}",
                    savedLike.getId(), commentId, userId
            );
        }

        log.info(
                "Comment liked: likeId={}, commentId={}, postId={}, likerId={}",
                savedLike.getId(), commentId, postId, userId
        );

        return likeMapper.toDTO(savedLike);
    }

    @Override
    public void unlikeComment(Long userId, Long postId, Long commentId) {
        findComment(postId, commentId);

        Like like = likeRepository
                .findByUserIdAndComment_Id(userId, commentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Like not found"
                ));

        enqueueDeletedEvent(like, userId, EventReason.USER_ACTION);
        likeRepository.delete(like);

        log.info(
                "Comment unliked: likeId={}, commentId={}, userId={}",
                like.getId(), commentId, userId
        );
    }

    /**
     * Removes likes and reverses scores for likes that previously earned points.
     */
    @Override
    public void removeLikesForPost(
            Post post,
            Long actorId,
            EventReason reason
    ) {
        List<Like> likes = likeRepository.findAllByPost_Id(post.getId());

        for (Like like : likes) {
            enqueueDeletedEvent(like, actorId, reason);
            likeRepository.delete(like);
        }
    }

    /**
     * Removes likes and reverses scores for likes that previously earned points.
     */
    @Override
    public void removeLikesForComment(
            Comment comment,
            Long actorId,
            EventReason reason
    ) {
        List<Like> likes =
                likeRepository.findAllByComment_Id(comment.getId());

        for (Like like : likes) {
            enqueueDeletedEvent(like, actorId, reason);
            likeRepository.delete(like);
        }
    }

    /**
     * Physically removes likes without reversing leaderboard scores.
     * Used when an owner voluntarily deletes a post.
     */
    @Override
    public void deleteLikesForPostWithoutScoreReversal(Post post) {
        List<Like> likes = likeRepository.findAllByPost_Id(post.getId());
        likeRepository.deleteAll(likes);
    }

    /**
     * Physically removes likes without reversing leaderboard scores.
     * Used when an owner voluntarily deletes a post.
     */
    @Override
    public void deleteLikesForCommentWithoutScoreReversal(Comment comment) {
        List<Like> likes =
                likeRepository.findAllByComment_Id(comment.getId());
        likeRepository.deleteAll(likes);
    }

    private void enqueueCreatedEvent(Like like, Long contentAuthorId) {
        // Self-likes are persisted but never scored.
        if (!isNotOwnContent(like.getUserId(), contentAuthorId)) {
            return;
        }

        String participantsJson = participantsJsonFactory.forLike(
                like.getUserId(),
                contentAuthorId
        );

        OutboxEntry outboxEntry =
                outboxEntryMapper.toCreatedLikeOutboxEntry(
                        like,
                        contentAuthorId,
                        participantsJson
                );

        outboxRepository.save(outboxEntry);
    }

    private void enqueueDeletedEvent(
            Like like,
            Long actorId,
            EventReason reason
    ) {
        Long contentAuthorId = getContentAuthorId(like);

        // Self-likes never had a CREATED scoring event.
        if (!isNotOwnContent(like.getUserId(), contentAuthorId)) {
            return;
        }

        boolean hasCreationEvent =
                outboxRepository.existsByContentIdAndEventTypeAndActionType(
                        like.getId(),
                        EventType.LIKE,
                        ActionType.CREATED
                );

        if (!hasCreationEvent) {
            log.debug(
                    "No scored LIKE/CREATED event; skipping reversal: likeId={}",
                    like.getId()
            );
            return;
        }

        String participantsJson = participantsJsonFactory.forLike(
                like.getUserId(),
                contentAuthorId
        );

        OutboxEntry outboxEntry =
                outboxEntryMapper.toDeletedLikeOutboxEntry(
                        like,
                        actorId,
                        contentAuthorId,
                        participantsJson,
                        reason
                );

        outboxRepository.save(outboxEntry);
    }

    private Long getContentAuthorId(Like like) {
        if (like.getPost() != null) {
            return like.getPost().getUserId();
        }

        return like.getComment().getUserId();
    }

    private boolean isNotOwnContent(Long userId, Long contentAuthorId) {
        return !Objects.equals(userId, contentAuthorId);
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Post not found"
                ));
    }

    private Comment findComment(Long postId, Long commentId) {
        return commentRepository.findByIdAndPost_Id(commentId, postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Comment not found"
                ));
    }
}