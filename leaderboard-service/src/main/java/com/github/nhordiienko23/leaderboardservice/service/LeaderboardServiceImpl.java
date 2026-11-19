package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardcontract.event.ScoreParticipant;
import com.github.nhordiienko23.leaderboardcontract.event.UserEvent;
import com.github.nhordiienko23.leaderboardservice.config.LeaderboardScoringProperties;
import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;
import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.entity.ProcessedEvent;
import com.github.nhordiienko23.leaderboardservice.entity.ProcessedUserEvent;
import com.github.nhordiienko23.leaderboardservice.entity.RedisSyncOutbox;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.ProcessedEventRepository;
import com.github.nhordiienko23.leaderboardservice.repository.ProcessedUserEventRepository;
import com.github.nhordiienko23.leaderboardservice.repository.RedisSyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardServiceImpl implements LeaderboardService {

    private static final String LEADERBOARD_KEY = "leaderboard";

    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ProcessedUserEventRepository processedUserEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisSyncOutboxRepository redisSyncOutboxRepository;
    private final LeaderboardScoringProperties scoringProperties;

    @Override
    @Transactional
    public void processLeaderboardEvent(LeaderboardEvent event) {
        Objects.requireNonNull(event, "Leaderboard event must not be null");
        Objects.requireNonNull(event.eventId(), "Event ID must not be null");
        Objects.requireNonNull(event.eventType(), "Event type must not be null");
        Objects.requireNonNull(event.actionType(), "Action type must not be null");
        Objects.requireNonNull(event.entityId(), "Entity ID must not be null");

        if (processedEventRepository.existsByEventIdAndEventTypeAndActionType(
                event.eventId(),
                event.eventType(),
                event.actionType()
        )) {
            log.debug(
                    "Duplicate event skipped: eventId={}, eventType={}, actionType={}",
                    event.eventId(),
                    event.eventType(),
                    event.actionType()
            );
            return;
        }

        if (event.participants().isEmpty()) {
            throw new IllegalArgumentException(
                    "Leaderboard event must contain participants: eventId="
                            + event.eventId()
            );
        }

        Map<Long, Long> pointsByUser = new LinkedHashMap<>();

        for (ScoreParticipant participant : event.participants()) {
            if (participant == null
                    || participant.userId() == null
                    || participant.role() == null) {
                throw new IllegalArgumentException(
                        "Participant userId and role are required: eventId="
                                + event.eventId()
                );
            }

            long points = scoringProperties.pointsFor(
                    event.eventType(),
                    event.actionType(),
                    participant.role()
            );

            pointsByUser.merge(
                    participant.userId(),
                    points,
                    (first, second) -> Math.addExact(first, second)
            );
        }

        for (var scoredUser : pointsByUser.entrySet()) {
            Long userId = scoredUser.getKey();
            long pointsDelta = scoredUser.getValue();

            LeaderboardEntry entry = leaderboardEntryRepository
                    .findByUserId(userId)
                    .orElseGet(() -> LeaderboardEntry.builder()
                            .userId(userId)
                            .username("user-" + userId)
                            .points(0L)
                            .build());

            long previousPoints = entry.getPoints();
            long newPoints = Math.max(
                    0L,
                    Math.addExact(previousPoints, pointsDelta)
            );

            entry.setPoints(newPoints);
            leaderboardEntryRepository.save(entry);

            redisSyncOutboxRepository.save(
                    RedisSyncOutbox.builder()
                            .userId(userId)
                            .processed(false)
                            .attempts(0)
                            .build()
            );

            log.info(
                    "Leaderboard score updated: eventId={}, entityId={}, "
                            + "eventType={}, actionType={}, userId={}, "
                            + "pointsDelta={}, score={} -> {}",
                    event.eventId(),
                    event.entityId(),
                    event.eventType(),
                    event.actionType(),
                    userId,
                    pointsDelta,
                    previousPoints,
                    newPoints
            );
        }

        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(event.eventId())
                        .eventType(event.eventType())
                        .actionType(event.actionType())
                        .build()
        );
    }

    @Override
    @Transactional
    public void processUserEvent(UserEvent event) {
        Objects.requireNonNull(event, "Leaderboard event must not be null");
        Objects.requireNonNull(event.eventId(), "Event ID must not be null");
        Objects.requireNonNull(event.userId(), "User ID must not be null");
        Objects.requireNonNull(event.actionType(), "Action type must not be null");
        Objects.requireNonNull(event.username(), "Username must not be null");

        if (processedUserEventRepository.existsById(
                event.eventId()
        )) {
            log.debug(
                    "Duplicate event skipped: eventId={}, userId={}, username={}, actionType={}",
                    event.eventId(),
                    event.userId(),
                    event.username(),
                    event.actionType()
            );
            return;
        }

        LeaderboardEntry leaderboardEntry = leaderboardEntryRepository.findByUserId(event.userId())
                .orElseGet(() -> LeaderboardEntry.builder()
                        .userId(event.userId())
                        .username(event.username())
                        .points(0L)
                        .build());

        leaderboardEntry.setUsername(event.username());
        leaderboardEntryRepository.save(leaderboardEntry);
        redisSyncOutboxRepository.save(
                RedisSyncOutbox.builder()
                        .userId(event.userId())
                        .processed(false)
                        .attempts(0)
                        .build()
        );

        ProcessedUserEvent processedUserEvent = ProcessedUserEvent.builder()
                .eventId(event.eventId())
                .userId(event.userId())
                .username(event.username())
                .eventType(event.actionType())
                .build();

        processedUserEventRepository.save(processedUserEvent);

        log.info(
                "Username for user successfullyUpdated userId={},username={}",
                event.userId(),
                event.username()
        );


    }

    @Override
    public List<LeaderboardEntryDTO> getLeaderboard(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        var users = redisTemplate.opsForZSet()
                .reverseRangeWithScores(LEADERBOARD_KEY, 0, limit - 1L);

        if (users == null || users.isEmpty()) {
            return List.of();
        }

        return users.stream()
                .filter(user ->
                        user.getValue() != null && user.getScore() != null
                )
                .map(user -> {
                    String userId = user.getValue();

                    String username = (String) redisTemplate.opsForHash()
                            .get("user:" + userId, "username");

                    return LeaderboardEntryDTO.builder()
                            .userId(Long.valueOf(userId))
                            .username(
                                    username != null
                                            ? username
                                            : "user-" + userId
                            )
                            .points(user.getScore().longValue())
                            .build();
                })
                .toList();
    }
}
