package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.dto.CommentCreationRequest;
import com.github.nhordiienko23.postservice.dto.CommentDTO;
import com.github.nhordiienko23.postservice.dto.CommentUpdateRequest;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.exception.notfound.CommentNotFoundException;
import com.github.nhordiienko23.postservice.exception.notfound.PostNotFoundException;
import com.github.nhordiienko23.postservice.mapper.CommentMapper;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final OutboxRepository outboxRepository;
    private final OutboxEntryMapper outboxEntryMapper;
    private final LeaderboardParticipantsJsonFactory participantsJsonFactory;
    private final CommentMapper commentMapper;
    private final LikeService likeService;

    @Override
    @Transactional
    public CommentDTO createComment(
            Long userId,
            Long postId,
            CommentCreationRequest request
    ) {
        Post post = findPost(postId);

        Comment comment = Comment.builder()
                .content(request.content())
                .userId(userId)
                .post(post)
                .build();

        Comment savedComment = commentRepository.save(comment);

        if (isSelfComment(savedComment)) {
            log.info(
                    "Self-comment created without leaderboard points: commentId={}, postId={}, userId={}",
                    savedComment.getId(), postId, userId
            );
        } else {
            String participantsJson = participantsJsonFactory.forComment(
                    savedComment.getUserId(),
                    post.getUserId()
            );

            OutboxEntry outboxEntry =
                    outboxEntryMapper.toCreatedCommentOutboxEntry(
                            savedComment,
                            participantsJson
                    );

            outboxRepository.save(outboxEntry);

            log.info(
                    "Comment leaderboard event queued: commentId={}, postId={}, commentAuthorId={}, postAuthorId={}, action={}",
                    savedComment.getId(),
                    postId,
                    savedComment.getUserId(),
                    post.getUserId(),
                    ActionType.CREATED
            );
        }

        return commentMapper.toDTO(savedComment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentDTO> getComments(Long postId, Pageable pageable) {
        findPost(postId);

        return commentRepository.findAllByPost_Id(postId, pageable)
                .map(commentMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentDTO getComment(Long postId, Long commentId) {
        findPost(postId);
        return commentMapper.toDTO(findComment(postId, commentId));
    }

    @Override
    @Transactional
    public CommentDTO updateComment(
            Long actorId,
            UserRole role,
            Long postId,
            Long commentId,
            CommentUpdateRequest request
    ) {
        findPost(postId);

        Comment comment = findComment(postId, commentId);
        checkPermission(comment, actorId, role);

        comment.setContent(request.content());

        return commentMapper.toDTO(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public void deleteComment(
            Long actorId,
            UserRole role,
            Long postId,
            Long commentId
    ) {
        findPost(postId);

        Comment comment = findComment(postId, commentId);
        checkPermission(comment, actorId, role);

        EventReason reason = role == UserRole.ADMIN
                ? EventReason.MODERATION
                : EventReason.USER_ACTION;

        // Reverse points from scored likes before deleting them.
        likeService.removeLikesForComment(comment, actorId, reason);

        // Reverse the comment's score if its creation was scored.
        enqueueCommentDeletionIfScored(comment, actorId, reason);

        commentRepository.delete(comment);

        log.info(
                "Comment deleted: commentId={}, postId={}, actorId={}, reason={}",
                commentId, postId, actorId, reason
        );
    }

    private void enqueueCommentDeletionIfScored(
            Comment comment,
            Long actorId,
            EventReason reason
    ) {
        if (isSelfComment(comment)) {
            return;
        }

        boolean hasCreationEvent =
                outboxRepository.existsByContentIdAndEventTypeAndActionType(
                        comment.getId(),
                        EventType.COMMENT,
                        ActionType.CREATED
                );

        if (!hasCreationEvent) {
            log.info(
                    "No comment creation event found; skipping score reversal: commentId={}",
                    comment.getId()
            );
            return;
        }

        String participantsJson = participantsJsonFactory.forComment(
                comment.getUserId(),
                comment.getPost().getUserId()
        );

        OutboxEntry outboxEntry =
                outboxEntryMapper.toDeletedCommentOutboxEntry(
                        comment,
                        actorId,
                        participantsJson,
                        reason
                );

        outboxRepository.save(outboxEntry);
    }

    private boolean isSelfComment(Comment comment) {
        return Objects.equals(
                comment.getUserId(),
                comment.getPost().getUserId()
        );
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(
                        "Post with id=" + postId + " not found"
                ));
    }

    private Comment findComment(Long postId, Long commentId) {
        return commentRepository.findByIdAndPost_Id(commentId, postId)
                .orElseThrow(() ->
                        new CommentNotFoundException(commentId, postId)
                );
    }

    private void checkPermission(
            Comment comment,
            Long actorId,
            UserRole role
    ) {
        boolean isAdmin = role == UserRole.ADMIN;

        if (!isAdmin && !Objects.equals(comment.getUserId(), actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can modify only your own comments"
            );
        }
    }
}