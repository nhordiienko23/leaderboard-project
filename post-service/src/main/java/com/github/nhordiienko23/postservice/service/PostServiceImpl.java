package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDto;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.kafka.PostCreatedEvent;
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
    public PostDto createPost(PostCreationRequest request) {
        Post post = postMapper.toEntity(request);
        Post savedPost = postRepository.save(post);
        log.info(
                "New post was created: postId={}, authorId={}",
                savedPost.getId(),
                savedPost.getAuthorId()
        );
        PostCreatedEvent event = postEventMapper.toEvent(savedPost);
        postEventProducer.sendPostCreatedEvent(event);
        return postMapper.toDto(savedPost);
    }

}
