package com.github.nhordiienko23.postservice.mapper;

import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.kafka.ActionType;
import com.github.nhordiienko23.postservice.kafka.ContentEvent;
import com.github.nhordiienko23.postservice.kafka.EventType;
import com.github.nhordiienko23.postservice.kafka.KafkaTopics;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        imports = {ActionType.class, EventType.class, KafkaTopics.class}
)
public interface OutboxEntryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "eventType", expression = "java(EventType.POST)")
    @Mapping(target = "topic", expression = "java(KafkaTopics.POST_EVENTS)")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "actionType", expression = "java(ActionType.CREATED)")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    OutboxEntry toCreatedOutboxEntry(Post post);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "eventType", expression = "java(EventType.POST)")
    @Mapping(target = "topic", expression = "java(KafkaTopics.POST_EVENTS)")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "actionType", expression = "java(ActionType.DELETED)")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    OutboxEntry toDeletedOutboxEntry(Post post);

    ContentEvent toContentEvent(OutboxEntry entry);
}