package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDTO;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.kafka.ContentEvent;
import com.github.nhordiienko23.postservice.kafka.KafkaTopics;
import com.github.nhordiienko23.postservice.kafka.PostEventProducer;
import com.github.nhordiienko23.postservice.mapper.PostEventMapper;
import com.github.nhordiienko23.postservice.mapper.PostMapper;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostRepository postRepository;
    private final PostEventMapper postEventMapper;
    private final PostEventProducer postEventProducer;

    @Override
    public PostDTO createPost(Long userId, PostCreationRequest request) {
        Post post = postMapper.toEntity(userId, request);
        Post savedPost = postRepository.save(post);
        log.info(
                "New post was created: postId={}, userId={}",
                savedPost.getId(),
                savedPost.getUserId()
        );
        ContentEvent event = postEventMapper.toCreatedEvent(savedPost);
        postEventProducer.publish(KafkaTopics.POST_EVENTS, event);
        return postMapper.toDTO(savedPost);
    }
    @Override
    public void deletePost(Long userId, Long postId) {
        Post post = postRepository.findByUserIdAndId(userId,postId)
                .orElseThrow(()->new IllegalArgumentException("post doesn't exists"));

        postRepository.delete(post);
        log.info(
                "Post was deleted: postId={}, userId={}",
                post.getId(),
                post.getUserId()
        );
        ContentEvent event = postEventMapper.toDeletedEvent(post);
        postEventProducer.publish(KafkaTopics.POST_EVENTS, event);
    }

    @Override
    public PostDTO getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(()->  new IllegalArgumentException("post not found"));
        return postMapper.toDTO(post);
    }

}
