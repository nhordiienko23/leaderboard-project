package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.dto.PostUpdateRequest;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.exception.accessdenied.PostAccessDeniedException;
import com.github.nhordiienko23.postservice.exception.notfound.PostNotFoundException;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.mapper.PostMapper;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final OutboxEntryMapper outboxEntryMapper;
    private final OutboxRepository outboxRepository;
    private final LikeService likeService;
    private final LeaderboardParticipantsJsonFactory participantsJsonFactory;

    @Override
    @Transactional
    public PostDTO createPost(Long userId, PostCreationRequest request) {
        Post post = postMapper.toEntity(userId, request);
        Post savedPost = postRepository.save(post);

        outboxRepository.save(
                outboxEntryMapper.toCreatedOutboxEntry(savedPost)
        );

        log.info(
                "Post created: postId={}, authorId={}",
                savedPost.getId(),
                savedPost.getUserId()
        );

        return postMapper.toDTO(savedPost);
    }

    @Override
    @Transactional
    public void deletePost(Long actorId, UserRole role, Long postId) {
        Post post = findPostById(postId);
        checkPostAccess(post, actorId, role);

        EventReason postReason = role == UserRole.ADMIN
                ? EventReason.MODERATION
                : EventReason.USER_ACTION;

        List<Comment> comments =
                commentRepository.findAllByPost_Id(post.getId());

        if (postReason == EventReason.MODERATION) {
            // Moderation: reverse points earned from the post and its likes.
            likeService.removeLikesForPost(
                    post,
                    actorId,
                    EventReason.PARENT_DELETED
            );

            // Also reverse points earned from comments and their likes.
            for (Comment comment : comments) {
                likeService.removeLikesForComment(
                        comment,
                        actorId,
                        EventReason.PARENT_DELETED
                );

                enqueueCommentDeletionIfScored(
                        comment,
                        actorId,
                        EventReason.PARENT_DELETED
                );
            }
        } else {
            // Voluntary deletion: remove dependent likes without reversing
            // the scores earned by other users through comments and likes.
            likeService.deleteLikesForPostWithoutScoreReversal(post);

            for (Comment comment : comments) {
                likeService.deleteLikesForCommentWithoutScoreReversal(
                        comment
                );
            }
        }

        OutboxEntry postDeletionEvent =
                outboxEntryMapper.toDeletedOutboxEntry(
                        post,
                        actorId,
                        postReason
                );

        outboxRepository.save(postDeletionEvent);
        postRepository.delete(post);

        log.info(
                "Post deleted: postId={}, actorId={}, authorId={}, reason={}",
                post.getId(),
                actorId,
                post.getUserId(),
                postReason
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PostDTO getPostById(Long id) {
        return postMapper.toDTO(findPostById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDTO> getAllPosts(Pageable pageable) {
        return postRepository.findAll(pageable)
                .map(postMapper::toDTO);
    }

    @Override
    @Transactional
    public PostDTO updatePost(
            Long actorId,
            UserRole role,
            Long postId,
            PostUpdateRequest request
    ) {
        Post post = findPostById(postId);
        checkPostAccess(post, actorId, role);

        post.setContent(request.content());

        log.info(
                "Post updated: postId={}, actorId={}, authorId={}",
                post.getId(),
                actorId,
                post.getUserId()
        );

        return postMapper.toDTO(post);
    }

    private void enqueueCommentDeletionIfScored(
            Comment comment,
            Long actorId,
            EventReason reason
    ) {
        if (Objects.equals(
                comment.getUserId(),
                comment.getPost().getUserId()
        )) {
            return;
        }

        boolean hasCreationEvent =
                outboxRepository.existsByContentIdAndEventTypeAndActionType(
                        comment.getId(),
                        EventType.COMMENT,
                        ActionType.CREATED
                );

        if (!hasCreationEvent) {
            log.debug(
                    "No scored COMMENT/CREATED event; skipping reversal: commentId={}",
                    comment.getId()
            );
            return;
        }

        String participantsJson = participantsJsonFactory.forComment(
                comment.getUserId(),
                comment.getPost().getUserId()
        );

        OutboxEntry deletionEvent =
                outboxEntryMapper.toDeletedCommentOutboxEntry(
                        comment,
                        actorId,
                        participantsJson,
                        reason
                );

        outboxRepository.save(deletionEvent);
    }

    private Post findPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(
                        "Post with id=" + postId + " not found"
                ));
    }

    private void checkPostAccess(
            Post post,
            Long actorId,
            UserRole role
    ) {
        boolean isAdmin = role == UserRole.ADMIN;
        boolean isOwner = Objects.equals(post.getUserId(), actorId);

        if (!isAdmin && !isOwner) {
            log.warn(
                    "Post access denied: postId={}, actorId={}, role={}",
                    post.getId(),
                    actorId,
                    role
            );

            throw new PostAccessDeniedException(
                    "You are not allowed to modify this post"
            );
        }
    }
}