package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.dto.PostUpdateRequest;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.exception.PostNotFoundException;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.mapper.PostMapper;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
        Post post = findByUserIdAndId(userId, postId);

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
    @Transactional(readOnly = true)
    public PostDTO getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException("Post with id=" + id + " not found"));
        return postMapper.toDTO(post);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDTO> getAllPosts(Pageable pageable) {
        Page<Post> postPage = postRepository.findAll(pageable);
        return postPage.map(postMapper::toDTO);
    }

    @Override
    @Transactional
    public PostDTO updatePost(Long userId, PostUpdateRequest request) {
        Post post = findByUserIdAndId(userId, request.postId());
        post.setContent(request.content());
        log.info(
                "Post with postId={} was updated by user with userId={}",
                post.getUserId(),
                post.getId()
        );
        return postMapper.toDTO(post);
    }

    private Post findByUserIdAndId(Long userId, Long postId) {
        return postRepository.findByUserIdAndId(userId, postId)
                .orElseThrow(() -> new PostNotFoundException(
                        "Post with id=%d was not found for user id=%d"
                                .formatted(postId, userId)
                ));
    }

}
