package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.exception.PostNotFoundException;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.mapper.PostMapper;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostRepository postRepository;
    private final OutboxEntryMapper outboxEntryMapper;
    private final OutboxRepository outboxRepository;


    @Override
    @Transactional
    public PostDTO createPost(Long userId, PostCreationRequest request) {
        Post post = postMapper.toEntity(userId, request);
        Post savedPost = postRepository.save(post);
        OutboxEntry outboxEntry = outboxEntryMapper.toCreatedOutboxEntry(savedPost);
        outboxRepository.save(outboxEntry);
        log.info(
                "New post was created: postId={}, userId={}",
                savedPost.getId(),
                savedPost.getUserId()
        );
        return postMapper.toDTO(savedPost);
    }

    @Override
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postRepository.findByUserIdAndId(userId, postId)
                .orElseThrow(() -> new PostNotFoundException(
                        "Post with id=%d was not found for user id=%d"
                                .formatted(postId, userId)
                ));

        postRepository.delete(post);
        OutboxEntry outboxEntry = outboxEntryMapper.toDeletedOutboxEntry(post);
        outboxRepository.save(outboxEntry);
        log.info(
                "Post was deleted: postId={}, userId={}",
                post.getId(),
                post.getUserId()
        );
    }

    @Override
    public PostDTO getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException("Post with id=" + id + " not found"));
        return postMapper.toDTO(post);
    }

}
