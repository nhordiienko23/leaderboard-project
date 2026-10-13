package com.github.nhordiienko23.postservice.mapper;

import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.kafka.PostCreatedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostEventMapper {
    @Mapping(target = "postId", source = "id")
    PostCreatedEvent toEvent(Post post);
}
