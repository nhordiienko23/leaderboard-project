package com.github.nhordiienko23.postservice.mapper;

import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.kafka.ContentEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostEventMapper {

    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "actionType", expression = "java(ActionType.CREATED)")
    ContentEvent toCreatedEvent(Post post);

    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "actionType", expression = "java(ActionType.DELETED)")
    ContentEvent toDeletedEvent(Post post);
}