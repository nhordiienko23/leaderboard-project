package com.github.nhordiienko23.postservice.mapper;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Like;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.kafka.KafkaTopics;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        imports = {
                ActionType.class,
                EventType.class,
                EventReason.class,
                KafkaTopics.class
        }
)
public interface OutboxEntryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contentId", source = "id")
    @Mapping(target = "eventType", expression = "java(EventType.POST)")
    @Mapping(target = "actionType", expression = "java(ActionType.CREATED)")
    @Mapping(target = "reason", expression = "java(EventReason.USER_ACTION)")
    @Mapping(target = "topic", expression = "java(KafkaTopics.LEADERBOARD_EVENTS)")
    @Mapping(target = "actorId", source = "userId")
    @Mapping(target = "targetAuthorId", ignore = true)
    @Mapping(target = "contentAuthorId", source = "userId")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "participantsJson", ignore = true)
    OutboxEntry toCreatedOutboxEntry(Post post);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contentId", source = "post.id")
    @Mapping(target = "eventType", expression = "java(EventType.POST)")
    @Mapping(target = "actionType", expression = "java(ActionType.DELETED)")
    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "topic", expression = "java(KafkaTopics.LEADERBOARD_EVENTS)")
    @Mapping(target = "actorId", source = "actorId")
    @Mapping(target = "targetAuthorId", ignore = true)
    @Mapping(target = "contentAuthorId", source = "post.userId")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "participantsJson", ignore = true)
    OutboxEntry toDeletedOutboxEntry(
            Post post,
            Long actorId,
            EventReason reason
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contentId", source = "comment.id")
    @Mapping(target = "eventType", expression = "java(EventType.COMMENT)")
    @Mapping(target = "actionType", expression = "java(ActionType.CREATED)")
    @Mapping(target = "reason", expression = "java(EventReason.USER_ACTION)")
    @Mapping(target = "topic", expression = "java(KafkaTopics.LEADERBOARD_EVENTS)")
    @Mapping(target = "actorId", source = "comment.userId")
    @Mapping(target = "targetAuthorId", source = "comment.post.userId")
    @Mapping(target = "contentAuthorId", source = "comment.userId")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "participantsJson", source = "participantsJson")
    OutboxEntry toCreatedCommentOutboxEntry(
            Comment comment,
            String participantsJson
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contentId", source = "comment.id")
    @Mapping(target = "eventType", expression = "java(EventType.COMMENT)")
    @Mapping(target = "actionType", expression = "java(ActionType.DELETED)")
    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "topic", expression = "java(KafkaTopics.LEADERBOARD_EVENTS)")
    @Mapping(target = "actorId", source = "actorId")
    @Mapping(target = "targetAuthorId", source = "comment.post.userId")
    @Mapping(target = "contentAuthorId", source = "comment.userId")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "participantsJson", source = "participantsJson")
    OutboxEntry toDeletedCommentOutboxEntry(
            Comment comment,
            Long actorId,
            String participantsJson,
            EventReason reason
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contentId", source = "like.id")
    @Mapping(target = "eventType", expression = "java(EventType.LIKE)")
    @Mapping(target = "actionType", expression = "java(ActionType.CREATED)")
    @Mapping(target = "reason", expression = "java(EventReason.USER_ACTION)")
    @Mapping(target = "topic", expression = "java(KafkaTopics.LEADERBOARD_EVENTS)")
    @Mapping(target = "actorId", source = "like.userId")
    @Mapping(target = "targetAuthorId", source = "contentAuthorId")
    @Mapping(target = "contentAuthorId", source = "contentAuthorId")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "participantsJson", source = "participantsJson")
    OutboxEntry toCreatedLikeOutboxEntry(
            Like like,
            Long contentAuthorId,
            String participantsJson
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contentId", source = "like.id")
    @Mapping(target = "eventType", expression = "java(EventType.LIKE)")
    @Mapping(target = "actionType", expression = "java(ActionType.DELETED)")
    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "topic", expression = "java(KafkaTopics.LEADERBOARD_EVENTS)")
    @Mapping(target = "actorId", source = "actorId")
    @Mapping(target = "targetAuthorId", source = "contentAuthorId")
    @Mapping(target = "contentAuthorId", source = "contentAuthorId")
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "attempts", expression = "java(0L)")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "participantsJson", source = "participantsJson")
    OutboxEntry toDeletedLikeOutboxEntry(
            Like like,
            Long actorId,
            Long contentAuthorId,
            String participantsJson,
            EventReason reason
    );

    // Backward-compatible overloads for existing callers/tests.
    default OutboxEntry toDeletedOutboxEntry(Post post) {
        return toDeletedOutboxEntry(
                post,
                post.getUserId(),
                EventReason.USER_ACTION
        );
    }

    default OutboxEntry toDeletedCommentOutboxEntry(
            Comment comment,
            Long actorId,
            String participantsJson
    ) {
        return toDeletedCommentOutboxEntry(
                comment,
                actorId,
                participantsJson,
                EventReason.USER_ACTION
        );
    }

    default OutboxEntry toDeletedLikeOutboxEntry(
            Like like,
            Long actorId,
            Long contentAuthorId,
            String participantsJson
    ) {
        return toDeletedLikeOutboxEntry(
                like,
                actorId,
                contentAuthorId,
                participantsJson,
                EventReason.USER_ACTION
        );
    }
}