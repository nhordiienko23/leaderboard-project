package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;
import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.entity.ProcessedPost;
import com.github.nhordiienko23.leaderboardservice.kafka.PostCreatedEvent;
import com.github.nhordiienko23.leaderboardservice.mapper.LeaderboardMapper;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.ProcessedPostRepository;
import com.github.nhordiienko23.leaderboardservice.score.ScorePoints;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardServiceImpl implements LeaderboardService {
    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final ProcessedPostRepository processedPostRepository;
    private final LeaderboardMapper leaderboardMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void processPostCreated(PostCreatedEvent event) {
        if (processedPostRepository.existsById(event.postId()))
            return;
        LeaderboardEntry entry = leaderboardEntryRepository.findByAuthorId(event.authorId())
                .orElseGet(() -> LeaderboardEntry.builder()
                        .authorId(event.authorId())
                        .points(0L)
                        .build());

        entry.setPoints(entry.getPoints() + ScorePoints.POST_CREATED);
        leaderboardEntryRepository.save(entry);

        redisTemplate.opsForZSet().add(
                "leaderboard",
                entry.getAuthorId().toString(),
                entry.getPoints().doubleValue()
        );

        processedPostRepository.save(ProcessedPost.builder()
                .postId(event.postId())
                .build());
        log.info(
                "Author {} received {} points for creating a post. Total score: {} points.",
                entry.getAuthorId(),
                ScorePoints.POST_CREATED,
                entry.getPoints()
        );
    }

    @Override
    public List<LeaderboardEntryDTO> getLeaderboard(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }

        var authorIds = redisTemplate.opsForZSet()
                .reverseRange("leaderboard", 0, limit - 1);

        if (authorIds == null || authorIds.isEmpty()) {
            return List.of();
        }

        return authorIds.stream()
                .map(Long::valueOf)
                .map(leaderboardEntryRepository::findByAuthorId)
                .flatMap(java.util.Optional::stream)
                .map(leaderboardMapper::toDTO)
                .toList();
    }
}
