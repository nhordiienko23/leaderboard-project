package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardcontract.event.ParticipantRole;
import com.github.nhordiienko23.leaderboardcontract.event.ScoreParticipant;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.kafka.PostEventProducer;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private final OutboxRepository outboxRepository;
    private final PostEventProducer postEventProducer;
    private final JsonMapper jsonMapper;

    @Scheduled(fixedDelay = 1000)
    public void processPendingEntries() {
        var entries = outboxRepository.findTop100ByPublishedFalseOrderByIdAsc();

        for (OutboxEntry entry : entries) {
            try {
                LeaderboardEvent event = toLeaderboardEvent(entry);

                postEventProducer.publish(entry.getTopic(), event);

                entry.setPublishedAt(LocalDateTime.now());
                entry.setPublished(true);
                entry.setLastError(null);

                log.info(
                        "Outbox event published: eventId={}, entityId={}, eventType={}, actionType={}, reason={}",
                        event.eventId(),
                        event.entityId(),
                        event.eventType(),
                        event.actionType(),
                        event.reason()
                );
            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);
                entry.setLastError(e.getMessage());

                log.error(
                        "Outbox processing failed: eventId={}, entityId={}, attempts={}",
                        entry.getId(),
                        entry.getContentId(),
                        entry.getAttempts(),
                        e
                );
            }
        }

        outboxRepository.saveAll(entries);
    }

    private LeaderboardEvent toLeaderboardEvent(OutboxEntry entry) {
        Objects.requireNonNull(entry.getId(), "Outbox ID must not be null");
        Objects.requireNonNull(entry.getEventType(), "Event type must not be null");
        Objects.requireNonNull(entry.getActionType(), "Action type must not be null");
        Objects.requireNonNull(entry.getContentId(), "Entity ID must not be null");

        List<ScoreParticipant> participants = resolveParticipants(entry);

        if (participants.isEmpty()) {
            throw new IllegalStateException(
                    "Outbox event must contain participants: eventId=" + entry.getId()
            );
        }

        return new LeaderboardEvent(
                entry.getId(),
                entry.getEventType(),
                entry.getActionType(),
                entry.getReason() == null
                        ? EventReason.USER_ACTION
                        : entry.getReason(),
                entry.getContentId(),
                participants
        );
    }

    private List<ScoreParticipant> resolveParticipants(OutboxEntry entry) {
        String participantsJson = entry.getParticipantsJson();

        if (participantsJson != null && !participantsJson.isBlank()) {
            try {
                return jsonMapper.readValue(
                        participantsJson,
                        new TypeReference<List<ScoreParticipant>>() {}
                );
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cannot deserialize outbox participants: eventId="
                                + entry.getId(),
                        e
                );
            }
        }

        // Compatibility with older post events that have no participantsJson.
        if (entry.getEventType() == EventType.POST) {
            Long postAuthorId = Objects.requireNonNull(
                    entry.getContentAuthorId(),
                    "Post author ID must not be null"
            );

            return List.of(
                    new ScoreParticipant(
                            postAuthorId,
                            ParticipantRole.POST_AUTHOR
                    )
            );
        }

        throw new IllegalStateException(
                "participantsJson is required for eventType="
                        + entry.getEventType()
                        + ", eventId=" + entry.getId()
        );
    }
}