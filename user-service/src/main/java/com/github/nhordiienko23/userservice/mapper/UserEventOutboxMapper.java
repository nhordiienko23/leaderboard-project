package com.github.nhordiienko23.userservice.mapper;

import com.github.nhordiienko23.leaderboardcontract.event.UserEvent;
import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import com.github.nhordiienko23.userservice.entity.UserEventOutbox;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserEventOutboxMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "eventType", source = "eventType")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "occurredAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    UserEventOutbox toOutbox(
            Long userId,
            String username,
            UserEventType eventType
    );

    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "actionType", source = "eventType")
    UserEvent toEvent(UserEventOutbox entry);
}