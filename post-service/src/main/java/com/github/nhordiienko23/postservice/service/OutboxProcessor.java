package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.kafka.ContentEvent;
import com.github.nhordiienko23.postservice.kafka.PostEventProducer;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {
    private final OutboxRepository outboxRepository;
    private final PostEventProducer postEventProducer;
    private final OutboxEntryMapper outboxEntryMapper;

    @Scheduled(fixedDelay = 1000)
    public void processPendingEntries() {
        var entries = outboxRepository.findTop100ByPublishedFalseOrderByIdAsc();

        for (OutboxEntry entry : entries) {
            try {
                ContentEvent event = outboxEntryMapper.toContentEvent(entry);

                postEventProducer.publish(entry.getTopic(), event);

                entry.setPublishedAt(LocalDateTime.now());
                entry.setPublished(true);
                entry.setLastError(null);

            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);
                entry.setLastError(e.getMessage());

                log.error(
                        "Event processing failed: eventId={}, eventType={}",
                        entry.getEventId(),
                        entry.getEventType(),
                        e
                );
            }
        }

        outboxRepository.saveAll(entries);
    }
}
