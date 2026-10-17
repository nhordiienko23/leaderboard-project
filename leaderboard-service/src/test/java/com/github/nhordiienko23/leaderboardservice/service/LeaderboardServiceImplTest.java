
package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardcontract.event.ParticipantRole;
import com.github.nhordiienko23.leaderboardcontract.event.ScoreParticipant;
import com.github.nhordiienko23.leaderboardservice.config.LeaderboardScoringProperties;
import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;
import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.entity.ProcessedEvent;
import com.github.nhordiienko23.leaderboardservice.entity.RedisSyncOutbox;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.ProcessedEventRepository;
import com.github.nhordiienko23.leaderboardservice.repository.RedisSyncOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceImplTest {

    private static final long POST_POINTS = 10L;
    private static final long COMMENT_AUTHOR_POINTS = 5L;
    private static final long CONTENT_AUTHOR_POINTS = 2L;
    private static final long LIKE_POINTS = 1L;

    @Mock
    private LeaderboardEntryRepository leaderboardEntryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisSyncOutboxRepository redisSyncOutboxRepository;

    @Mock
    private LeaderboardScoringProperties scoringProperties;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private LeaderboardServiceImpl leaderboardService;

    @Test
    void createdPostShouldIncreaseExistingUserScore() {
        LeaderboardEntry entry = leaderboardEntry(23L, "Nikita", 20L);

        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        1L, EventType.POST, ActionType.CREATED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.POST,
                ActionType.CREATED,
                ParticipantRole.POST_AUTHOR
        )).thenReturn(POST_POINTS);

        when(leaderboardEntryRepository.findByUserId(23L))
                .thenReturn(Optional.of(entry));

        leaderboardService.processLeaderboardEvent(
                event(
                        1L,
                        EventType.POST,
                        ActionType.CREATED,
                        100L,
                        participant(23L, ParticipantRole.POST_AUTHOR)
                )
        );

        assertEquals(30L, entry.getPoints());

        verify(leaderboardEntryRepository).save(entry);
        verify(redisSyncOutboxRepository).save(any(RedisSyncOutbox.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void createdPostShouldCreateEntryForNewUser() {
        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        2L, EventType.POST, ActionType.CREATED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.POST,
                ActionType.CREATED,
                ParticipantRole.POST_AUTHOR
        )).thenReturn(POST_POINTS);

        when(leaderboardEntryRepository.findByUserId(42L))
                .thenReturn(Optional.empty());

        leaderboardService.processLeaderboardEvent(
                event(
                        2L,
                        EventType.POST,
                        ActionType.CREATED,
                        200L,
                        participant(42L, ParticipantRole.POST_AUTHOR)
                )
        );

        ArgumentCaptor<LeaderboardEntry> captor =
                ArgumentCaptor.forClass(LeaderboardEntry.class);

        verify(leaderboardEntryRepository).save(captor.capture());

        LeaderboardEntry saved = captor.getValue();

        assertEquals(42L, saved.getUserId());
        assertEquals("user-42", saved.getUsername());
        assertEquals(POST_POINTS, saved.getPoints());

        verify(redisSyncOutboxRepository).save(any(RedisSyncOutbox.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void deletedPostShouldDecreaseAuthorScore() {
        LeaderboardEntry entry = leaderboardEntry(23L, "Nikita", 30L);

        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        3L, EventType.POST, ActionType.DELETED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.POST,
                ActionType.DELETED,
                ParticipantRole.POST_AUTHOR
        )).thenReturn(-POST_POINTS);

        when(leaderboardEntryRepository.findByUserId(23L))
                .thenReturn(Optional.of(entry));

        leaderboardService.processLeaderboardEvent(
                event(
                        3L,
                        EventType.POST,
                        ActionType.DELETED,
                        100L,
                        participant(23L, ParticipantRole.POST_AUTHOR)
                )
        );

        assertEquals(20L, entry.getPoints());

        verify(leaderboardEntryRepository).save(entry);
        verify(redisSyncOutboxRepository).save(any(RedisSyncOutbox.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void deletedPostShouldNeverReduceScoreBelowZero() {
        LeaderboardEntry entry = leaderboardEntry(23L, "Nikita", 3L);

        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        4L, EventType.POST, ActionType.DELETED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.POST,
                ActionType.DELETED,
                ParticipantRole.POST_AUTHOR
        )).thenReturn(-POST_POINTS);

        when(leaderboardEntryRepository.findByUserId(23L))
                .thenReturn(Optional.of(entry));

        leaderboardService.processLeaderboardEvent(
                event(
                        4L,
                        EventType.POST,
                        ActionType.DELETED,
                        100L,
                        participant(23L, ParticipantRole.POST_AUTHOR)
                )
        );

        assertEquals(0L, entry.getPoints());

        verify(leaderboardEntryRepository).save(entry);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void createdCommentShouldUseCommentAuthorRule() {
        LeaderboardEntry entry = leaderboardEntry(42L, "user-42", 0L);

        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        5L, EventType.COMMENT, ActionType.CREATED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.COMMENT,
                ActionType.CREATED,
                ParticipantRole.COMMENT_AUTHOR
        )).thenReturn(COMMENT_AUTHOR_POINTS);

        when(leaderboardEntryRepository.findByUserId(42L))
                .thenReturn(Optional.of(entry));

        leaderboardService.processLeaderboardEvent(
                event(
                        5L,
                        EventType.COMMENT,
                        ActionType.CREATED,
                        300L,
                        participant(42L, ParticipantRole.COMMENT_AUTHOR)
                )
        );

        assertEquals(COMMENT_AUTHOR_POINTS, entry.getPoints());

        verify(leaderboardEntryRepository).save(entry);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void createdLikeShouldUseContentAuthorRule() {
        LeaderboardEntry entry = leaderboardEntry(42L, "user-42", 0L);

        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        6L, EventType.LIKE, ActionType.CREATED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.LIKE,
                ActionType.CREATED,
                ParticipantRole.CONTENT_AUTHOR
        )).thenReturn(LIKE_POINTS);

        when(leaderboardEntryRepository.findByUserId(42L))
                .thenReturn(Optional.of(entry));

        leaderboardService.processLeaderboardEvent(
                event(
                        6L,
                        EventType.LIKE,
                        ActionType.CREATED,
                        400L,
                        participant(42L, ParticipantRole.CONTENT_AUTHOR)
                )
        );

        assertEquals(LIKE_POINTS, entry.getPoints());

        verify(leaderboardEntryRepository).save(entry);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void deletedLikeShouldDecreaseContentAuthorScore() {
        LeaderboardEntry entry = leaderboardEntry(42L, "user-42", 10L);

        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        7L, EventType.LIKE, ActionType.DELETED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.LIKE,
                ActionType.DELETED,
                ParticipantRole.CONTENT_AUTHOR
        )).thenReturn(-LIKE_POINTS);

        when(leaderboardEntryRepository.findByUserId(42L))
                .thenReturn(Optional.of(entry));

        leaderboardService.processLeaderboardEvent(
                event(
                        7L,
                        EventType.LIKE,
                        ActionType.DELETED,
                        400L,
                        participant(42L, ParticipantRole.CONTENT_AUTHOR)
                )
        );

        assertEquals(9L, entry.getPoints());

        verify(leaderboardEntryRepository).save(entry);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void commentShouldApplyDifferentRulesToDifferentRoles() {
        LeaderboardEntry commentAuthor =
                leaderboardEntry(23L, "commenter", 0L);
        LeaderboardEntry contentAuthor =
                leaderboardEntry(42L, "post-author", 10L);

        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        8L, EventType.COMMENT, ActionType.CREATED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.COMMENT,
                ActionType.CREATED,
                ParticipantRole.COMMENT_AUTHOR
        )).thenReturn(COMMENT_AUTHOR_POINTS);

        when(scoringProperties.pointsFor(
                EventType.COMMENT,
                ActionType.CREATED,
                ParticipantRole.CONTENT_AUTHOR
        )).thenReturn(CONTENT_AUTHOR_POINTS);

        when(leaderboardEntryRepository.findByUserId(23L))
                .thenReturn(Optional.of(commentAuthor));
        when(leaderboardEntryRepository.findByUserId(42L))
                .thenReturn(Optional.of(contentAuthor));

        leaderboardService.processLeaderboardEvent(
                event(
                        8L,
                        EventType.COMMENT,
                        ActionType.CREATED,
                        500L,
                        participant(23L, ParticipantRole.COMMENT_AUTHOR),
                        participant(42L, ParticipantRole.CONTENT_AUTHOR)
                )
        );

        assertEquals(5L, commentAuthor.getPoints());
        assertEquals(12L, contentAuthor.getPoints());

        verify(leaderboardEntryRepository, times(2))
                .save(any(LeaderboardEntry.class));
        verify(redisSyncOutboxRepository, times(2))
                .save(any(RedisSyncOutbox.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void sameUserInTwoRolesShouldReceiveCombinedPointsAndBeSavedOnce() {
        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        9L, EventType.COMMENT, ActionType.CREATED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.COMMENT,
                ActionType.CREATED,
                ParticipantRole.COMMENT_AUTHOR
        )).thenReturn(COMMENT_AUTHOR_POINTS);

        when(scoringProperties.pointsFor(
                EventType.COMMENT,
                ActionType.CREATED,
                ParticipantRole.CONTENT_AUTHOR
        )).thenReturn(CONTENT_AUTHOR_POINTS);

        when(leaderboardEntryRepository.findByUserId(23L))
                .thenReturn(Optional.empty());

        leaderboardService.processLeaderboardEvent(
                event(
                        9L,
                        EventType.COMMENT,
                        ActionType.CREATED,
                        600L,
                        participant(23L, ParticipantRole.COMMENT_AUTHOR),
                        participant(23L, ParticipantRole.CONTENT_AUTHOR)
                )
        );

        ArgumentCaptor<LeaderboardEntry> captor =
                ArgumentCaptor.forClass(LeaderboardEntry.class);

        verify(leaderboardEntryRepository).save(captor.capture());

        assertEquals(23L, captor.getValue().getUserId());
        assertEquals(
                COMMENT_AUTHOR_POINTS + CONTENT_AUTHOR_POINTS,
                captor.getValue().getPoints()
        );

        verify(leaderboardEntryRepository, times(1)).findByUserId(23L);
        verify(redisSyncOutboxRepository, times(1))
                .save(any(RedisSyncOutbox.class));
    }

    @Test
    void duplicateEventShouldNotChangeScoresAgain() {
        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        10L, EventType.POST, ActionType.CREATED
                )).thenReturn(true);

        leaderboardService.processLeaderboardEvent(
                event(
                        10L,
                        EventType.POST,
                        ActionType.CREATED,
                        700L,
                        participant(23L, ParticipantRole.POST_AUTHOR)
                )
        );

        verifyNoInteractions(
                leaderboardEntryRepository,
                redisSyncOutboxRepository,
                scoringProperties
        );

        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    @Test
    void eventWithoutParticipantsShouldBeRejected() {
        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        11L, EventType.POST, ActionType.CREATED
                )).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> leaderboardService.processLeaderboardEvent(
                        event(
                                11L,
                                EventType.POST,
                                ActionType.CREATED,
                                800L
                        )
                )
        );

        verifyNoInteractions(
                leaderboardEntryRepository,
                redisSyncOutboxRepository,
                scoringProperties
        );

        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    @Test
    void participantWithoutUserIdShouldBeRejected() {
        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        12L, EventType.POST, ActionType.CREATED
                )).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> leaderboardService.processLeaderboardEvent(
                        event(
                                12L,
                                EventType.POST,
                                ActionType.CREATED,
                                900L,
                                participant(null, ParticipantRole.POST_AUTHOR)
                        )
                )
        );

        verifyNoInteractions(
                leaderboardEntryRepository,
                redisSyncOutboxRepository,
                scoringProperties
        );

        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    @Test
    void participantWithoutRoleShouldBeRejected() {
        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        13L, EventType.POST, ActionType.CREATED
                )).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> leaderboardService.processLeaderboardEvent(
                        event(
                                13L,
                                EventType.POST,
                                ActionType.CREATED,
                                1000L,
                                participant(23L, null)
                        )
                )
        );

        verifyNoInteractions(
                leaderboardEntryRepository,
                redisSyncOutboxRepository,
                scoringProperties
        );

        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    @Test
    void missingScoringRuleShouldBeRejectedBeforeUpdatingScore() {
        when(processedEventRepository
                .existsByEventIdAndEventTypeAndActionType(
                        14L, EventType.ORDER, ActionType.CREATED
                )).thenReturn(false);

        when(scoringProperties.pointsFor(
                EventType.ORDER,
                ActionType.CREATED,
                ParticipantRole.BUYER
        )).thenThrow(new IllegalArgumentException(
                "Scoring rule is not configured"
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> leaderboardService.processLeaderboardEvent(
                        event(
                                14L,
                                EventType.ORDER,
                                ActionType.CREATED,
                                1100L,
                                participant(23L, ParticipantRole.BUYER)
                        )
                )
        );

        verify(leaderboardEntryRepository, never())
                .findByUserId(any(Long.class));
        verify(leaderboardEntryRepository, never())
                .save(any(LeaderboardEntry.class));
        verify(redisSyncOutboxRepository, never())
                .save(any(RedisSyncOutbox.class));
        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    @Test
    void nonPositiveLeaderboardLimitShouldReturnEmptyList() {
        assertTrue(leaderboardService.getLeaderboard(0).isEmpty());
        assertTrue(leaderboardService.getLeaderboard(-1).isEmpty());

        verifyNoInteractions(redisTemplate, zSetOperations, hashOperations);
    }

    @Test
    void getLeaderboardShouldMapRedisScoresAndFallbackUsername() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        Set<ZSetOperations.TypedTuple<String>> tuples =
                new LinkedHashSet<>();

        tuples.add(tuple("23", 30.0));
        tuples.add(tuple("2", 10.0));

        when(zSetOperations.reverseRangeWithScores(
                "leaderboard",
                0L,
                9L
        )).thenReturn(tuples);

        when(hashOperations.get("user:23", "username"))
                .thenReturn("Nikita");
        when(hashOperations.get("user:2", "username"))
                .thenReturn(null);

        List<LeaderboardEntryDTO> result =
                leaderboardService.getLeaderboard(10);

        assertEquals(2, result.size());

        Map<Long, LeaderboardEntryDTO> resultByUserId =
                result.stream()
                        .collect(Collectors.toMap(
                                LeaderboardEntryDTO::userId,
                                Function.identity()
                        ));

        assertEquals("Nikita", resultByUserId.get(23L).username());
        assertEquals(30L, resultByUserId.get(23L).points());

        assertEquals("user-2", resultByUserId.get(2L).username());
        assertEquals(10L, resultByUserId.get(2L).points());
    }

    @Test
    void emptyRedisLeaderboardShouldReturnEmptyList() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        when(zSetOperations.reverseRangeWithScores(
                "leaderboard",
                0L,
                9L
        )).thenReturn(Set.of());

        assertTrue(leaderboardService.getLeaderboard(10).isEmpty());
    }

    @Test
    void nullRedisResultShouldReturnEmptyList() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        when(zSetOperations.reverseRangeWithScores(
                "leaderboard",
                0L,
                9L
        )).thenReturn(null);

        assertTrue(leaderboardService.getLeaderboard(10).isEmpty());
    }

    private LeaderboardEntry leaderboardEntry(
            Long userId,
            String username,
            Long points
    ) {
        return LeaderboardEntry.builder()
                .userId(userId)
                .username(username)
                .points(points)
                .build();
    }

    private LeaderboardEvent event(
            Long eventId,
            EventType eventType,
            ActionType actionType,
            Long entityId,
            ScoreParticipant... participants
    ) {
        return new LeaderboardEvent(
                eventId,
                eventType,
                actionType,
                entityId,
                List.of(participants)
        );
    }

    private ScoreParticipant participant(
            Long userId,
            ParticipantRole role
    ) {
        return new ScoreParticipant(userId, role);
    }

    @SuppressWarnings("unchecked")
    private ZSetOperations.TypedTuple<String> tuple(
            String userId,
            Double score
    ) {
        ZSetOperations.TypedTuple<String> tuple =
                Mockito.mock(ZSetOperations.TypedTuple.class);

        when(tuple.getValue()).thenReturn(userId);
        when(tuple.getScore()).thenReturn(score);

        return tuple;
    }
}
