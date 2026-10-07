package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.dto.PostCreationRequest;
import com.github.nhordiienko23.postservice.dto.PostDto;

import com.github.nhordiienko23.postservice.mapper.PostMapper;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {
    private final PostMapper postMapper;
    private final PostRepository postRepository;


    @Override
    @Transactional
    public PostDto createPost(PostCreationRequest request) {
        return postMapper.toDto(postRepository.save(postMapper.toEntity(request)));
    }

}
