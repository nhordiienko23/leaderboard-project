package com.github.nhordiienko23.leaderboardservice.kafka;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.ProcessedEventRepository;
import com.github.nhordiienko23.leaderboardservice.repository.RedisSyncOutboxRepository;
import com.github.nhordiienko23.leaderboardservice.testcontainers.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardEventListenerIntegrationTest extends AbstractIntegrationTest {

    private static final String LEADERBOARD_KEY = "leaderboard";
    private static final String MESSAGE_KEY = "leaderboard-integration-test";

    @Autowired
    private LeaderboardEntryRepository leaderboardEntryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private RedisSyncOutboxRepository redisSyncOutboxRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisSyncOutboxRepository.deleteAll();
        processedEventRepository.deleteAll();
        leaderboardEntryRepository.deleteAll();

        redisTemplate.delete(LEADERBOARD_KEY);

        Set<String> userKeys = redisTemplate.keys("user:*");
        if (userKeys != null && !userKeys.isEmpty()) {
            redisTemplate.delete(userKeys);
        }
    }

    @Test
    void shouldProcessPostCreatedEventFromKafka() throws Exception {
        long eventId = 91001L;
        long userId = 101L;

        sendPostCreatedEvent(eventId, 50001L, userId);

        awaitCreatedEventProcessed(eventId, EventType.POST);
        awaitUserSynced(userId, 10L);
    }

    @Test
    void shouldNotApplyDuplicateEventTwice() throws Exception {
        long eventId = 92001L;
        long userId = 201L;

        sendPostCreatedEvent(eventId, 50002L, userId);
        sendPostCreatedEvent(eventId, 50002L, userId);


        long markerEventId = 92002L;
        long markerUserId = 202L;

        sendPostCreatedEvent(markerEventId, 50003L, markerUserId);

        awaitCreatedEventProcessed(markerEventId, EventType.POST);
        awaitUserSynced(markerUserId, 10L);
        awaitCreatedEventProcessed(eventId, EventType.POST);

        LeaderboardEntry entry = leaderboardEntryRepository
                .findByUserId(userId)
                .orElseThrow();

        assertThat(entry.getPoints()).isEqualTo(10L);

        long outboxTasksForUser = redisSyncOutboxRepository.findAll()
                .stream()
                .filter(task -> task.getUserId().equals(userId))
                .count();

        assertThat(outboxTasksForUser).isEqualTo(1L);
    }

    @Test
    void shouldAwardPointsToCommentAuthorAndContentAuthor()
            throws Exception {

        long eventId = 93001L;
        long commentAuthorId = 301L;
        long contentAuthorId = 302L;

        String eventJson = """
                {
                  "eventId": 93001,
                  "eventType": "COMMENT",
                  "actionType": "CREATED",
                  "entityId": 70001,
                  "participants": [
                    {
                      "userId": 301,
                      "role": "COMMENT_AUTHOR"
                    },
                    {
                      "userId": 302,
                      "role": "CONTENT_AUTHOR"
                    }
                  ]
                }
                """;

        sendEvent(eventJson);

        awaitCreatedEventProcessed(eventId, EventType.COMMENT);
        awaitUserSynced(commentAuthorId, 5L);
        awaitUserSynced(contentAuthorId, 2L);
    }

    private void sendPostCreatedEvent(
            long eventId,
            long entityId,
            long userId
    ) throws Exception {

        String eventJson = """
                {
                  "eventId": %d,
                  "eventType": "POST",
                  "actionType": "CREATED",
                  "entityId": %d,
                  "participants": [
                    {
                      "userId": %d,
                      "role": "POST_AUTHOR"
                    }
                  ]
                }
                """.formatted(eventId, entityId, userId);

        sendEvent(eventJson);
    }

    private void sendEvent(String eventJson) throws Exception {
        try (KafkaProducer<String, String> producer =
                     new KafkaProducer<>(producerProperties())) {

            producer.send(
                    new ProducerRecord<>(
                            KafkaTopics.LEADERBOARD_EVENTS,
                            MESSAGE_KEY,
                            eventJson
                    )
            ).get(10, TimeUnit.SECONDS);
        }
    }

    private Properties producerProperties() {
        Properties properties = new Properties();

        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );
        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );
        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );
        properties.put(ProducerConfig.ACKS_CONFIG, "all");

        return properties;
    }

    private void awaitCreatedEventProcessed(
            long eventId,
            EventType eventType
    ) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() ->
                        assertThat(
                                processedEventRepository
                                        .existsByEventIdAndEventTypeAndActionType(
                                                eventId,
                                                eventType,
                                                ActionType.CREATED
                                        )
                        ).isTrue()
                );
    }

    private void awaitUserSynced(long userId, long expectedPoints) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    LeaderboardEntry entry = leaderboardEntryRepository
                            .findByUserId(userId)
                            .orElseThrow();

                    assertThat(entry.getPoints()).isEqualTo(expectedPoints);

                    var tasks = redisSyncOutboxRepository.findAll()
                            .stream()
                            .filter(task -> task.getUserId().equals(userId))
                            .toList();

                    assertThat(tasks).isNotEmpty();
                    assertThat(tasks)
                            .allSatisfy(task ->
                                    assertThat(task.isProcessed()).isTrue()
                            );

                    Double score = redisTemplate.opsForZSet()
                            .score(LEADERBOARD_KEY, String.valueOf(userId));

                    assertThat(score).isEqualTo((double) expectedPoints);

                    Object username = redisTemplate.opsForHash()
                            .get("user:" + userId, "username");

                    assertThat(username).isEqualTo("user-" + userId);
                });
    }
}
