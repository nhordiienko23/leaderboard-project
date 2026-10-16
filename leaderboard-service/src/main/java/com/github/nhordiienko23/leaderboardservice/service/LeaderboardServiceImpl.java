package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;
import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.entity.ProcessedEvent;
import com.github.nhordiienko23.leaderboardservice.entity.RedisSyncOutbox;
import com.github.nhordiienko23.leaderboardservice.kafka.ActionType;
import com.github.nhordiienko23.leaderboardservice.kafka.ContentEvent;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.ProcessedEventRepository;
import com.github.nhordiienko23.leaderboardservice.repository.RedisSyncOutboxRepository;
import com.github.nhordiienko23.leaderboardservice.score.ScorePoints;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardServiceImpl implements LeaderboardService {
    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisSyncOutboxRepository redisSyncOutboxRepository;

    @Override
    @Transactional
    public void processPostEvent(ContentEvent event) {
        processContentEvent(event, EventType.POST, ScorePoints.POST);
    }


    @Override
    public List<LeaderboardEntryDTO> getLeaderboard(int limit) {

        var users = redisTemplate.opsForZSet()
                .reverseRangeWithScores("leaderboard", 0, limit - 1);

        if (users == null || users.isEmpty()) {
            return List.of();
        }

        return users.stream()
                .map(user -> {
                    String userId = user.getValue();

                    String username = (String) redisTemplate.opsForHash()
                            .get("user:" + userId, "username");

                    return LeaderboardEntryDTO.builder()
                            .userId(Long.valueOf(userId))
                            .username(username)
                            .points(user.getScore().longValue())
                            .build();
                }).toList();
    }

    private void processContentEvent(
            ContentEvent event,
            EventType eventType,
            long points
    ) {
        String type = eventType.name();
        String action = event.actionType().name();

        if (processedEventRepository.existsByEventIdAndEventTypeAndActionType(
                event.eventId(), type, action)) {
            log.debug(
                    "Duplicate event skipped: type={}, eventId={}, action={}",
                    type, event.eventId(), action
            );
            return;
        }

        LeaderboardEntry entry = leaderboardEntryRepository
                .findByUserId(event.userId())
                .orElseGet(() -> LeaderboardEntry.builder()
                        .userId(event.userId())
                        .username("user-" + event.userId())
                        .points(0L)
                        .build());

        long delta = event.actionType() == ActionType.CREATED
                ? points
                : -points;

        long previousPoints = entry.getPoints();
        long newPoints = Math.max(0L, previousPoints + delta);

        entry.setPoints(newPoints);
        leaderboardEntryRepository.save(entry);

        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(event.eventId())
                .eventType(type)
                .actionType(action)
                .build());


        redisSyncOutboxRepository.save(RedisSyncOutbox.builder()
                .userId(entry.getUserId())
                .processed(false)
                .attempts(0)
                .build());

        String userId = entry.getUserId().toString();
        
        log.info(
                "Leaderboard event processed: type={}, eventId={}, action={}, " +
                        "userId={}, pointsDelta={}, score={} -> {}",
                type,
                event.eventId(),
                action,
                userId,
                delta,
                previousPoints,
                newPoints
        );
    }
}
