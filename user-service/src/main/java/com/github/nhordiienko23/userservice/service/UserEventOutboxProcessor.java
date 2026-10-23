package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.UserEvent;
import com.github.nhordiienko23.userservice.entity.UserEventOutbox;
import com.github.nhordiienko23.userservice.kafka.KafkaTopics;
import com.github.nhordiienko23.userservice.kafka.UserEventProducer;
import com.github.nhordiienko23.userservice.mapper.UserEventOutboxMapper;
import com.github.nhordiienko23.userservice.repository.UserEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventOutboxProcessor {

    private final UserEventOutboxRepository outboxRepository;
    private final UserEventProducer userEventProducer;
    private final UserEventOutboxMapper outboxMapper;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processPendingEntries() {
        List<UserEventOutbox> entries =
                outboxRepository.findTop100ByPublishedFalseOrderByIdAsc();

        for (UserEventOutbox entry : entries) {
            try {
                UserEvent event = outboxMapper.toEvent(entry);

                userEventProducer.publish(
                        KafkaTopics.USER_EVENTS,
                        event
                );

                entry.setPublished(true);
                entry.setPublishedAt(java.time.Instant.now());
                entry.setLastError(null);

                log.info(
                        "User outbox event published: eventId={}, userId={}, eventType={}",
                        entry.getId(),
                        entry.getUserId(),
                        entry.getEventType()
                );

            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);

                String error = e.getMessage() == null
                        ? e.getClass().getSimpleName()
                        : e.getMessage();

                entry.setLastError(
                        error.length() <= 2000
                                ? error
                                : error.substring(0, 2000)
                );

                log.error(
                        "User outbox processing failed: eventId={}, userId={}, attempts={}",
                        entry.getId(),
                        entry.getUserId(),
                        entry.getAttempts(),
                        e
                );
            }
        }

        outboxRepository.saveAll(entries);
    }
}