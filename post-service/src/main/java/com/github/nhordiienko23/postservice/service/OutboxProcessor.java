package com.github.nhordiienko23.postservice.service;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private final OutboxRepository outboxRepository;
    private final PostEventProducer postEventProducer;

    @Scheduled(fixedDelay = 1000)
    public void processPendingEntries() {
        var entries = outboxRepository
                .findTop100ByPublishedFalseOrderByIdAsc();

        for (OutboxEntry entry : entries) {
            try {
                LeaderboardEvent event = toLeaderboardEvent(entry);

                postEventProducer.publish(entry.getTopic(), event);

                entry.setPublishedAt(LocalDateTime.now());
                entry.setPublished(true);
                entry.setLastError(null);

                log.info(
                        "Outbox event published: eventId={}, postId={}, "
                                + "actionType={}",
                        event.eventId(),
                        event.entityId(),
                        event.actionType()
                );

            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);
                entry.setLastError(e.getMessage());

                log.error(
                        "Outbox event processing failed: eventId={}, "
                                + "postId={}, attempts={}",
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
        Long authorId = Objects.requireNonNull(
                entry.getContentAuthorId(),
                "Post author ID must not be null"
        );

        return new LeaderboardEvent(
                entry.getId(),
                entry.getEventType(),
                entry.getActionType(),
                entry.getContentId(),
                List.of(
                        new ScoreParticipant(
                                authorId,
                                ParticipantRole.POST_AUTHOR
                        )
                )
        );
    }
}