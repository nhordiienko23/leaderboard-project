package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.entity.ProcessedPost;
import com.github.nhordiienko23.leaderboardservice.kafka.PostCreatedEvent;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.ProcessedPostRepository;
import com.github.nhordiienko23.leaderboardservice.score.ScorePoints;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardServiceImpl implements LeaderboardService {
    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final ProcessedPostRepository processedPostRepository;

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
}
