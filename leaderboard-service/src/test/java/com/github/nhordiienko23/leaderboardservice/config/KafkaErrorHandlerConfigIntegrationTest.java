package com.github.nhordiienko23.leaderboardservice.config;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.kafka.KafkaTopics;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.ProcessedEventRepository;
import com.github.nhordiienko23.leaderboardservice.repository.RedisSyncOutboxRepository;
import com.github.nhordiienko23.leaderboardservice.service.LeaderboardService;
import com.github.nhordiienko23.leaderboardservice.testcontainers.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class KafkaErrorHandlerConfigIntegrationTest
        extends AbstractIntegrationTest {

    private static final String LEADERBOARD_KEY = "leaderboard";
    private static final String DLT_TOPIC =
            KafkaTopics.LEADERBOARD_EVENTS + "-dlt";

    private static final String MESSAGE_KEY =
            "kafka-error-handler-integration-test";

    private static final AtomicLong EVENT_IDS =
            new AtomicLong(System.currentTimeMillis());

    @Autowired
    private LeaderboardEntryRepository leaderboardEntryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private RedisSyncOutboxRepository redisSyncOutboxRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private LeaderboardService leaderboardService;

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
    void shouldSendIllegalArgumentExceptionEventToDltWithoutRetries()
            throws Exception {

        long eventId = nextId();

        String eventJson = """
                {
                  "eventId": %d,
                  "eventType": "POST",
                  "actionType": "CREATED",
                  "entityId": %d,
                  "participants": []
                }
                """.formatted(eventId, nextId());

        sendEvent(eventJson);

        String dltMessage = awaitDltEvent(eventId);

        assertThat(dltMessage).contains("\"eventId\":" + eventId);

        verify(leaderboardService, times(1))
                .processLeaderboardEvent(argThat(
                        event -> event != null
                                && event.eventId() != null
                                && event.eventId() == eventId
                ));

        assertThat(
                processedEventRepository.existsByEventIdAndEventTypeAndActionType(
                        eventId,
                        EventType.POST,
                        ActionType.CREATED
                )
        ).isFalse();
    }

    @Test
    void shouldRetryRetryableExceptionTwiceAndThenSendEventToDlt()
            throws Exception {

        long eventId = nextId();
        long userId = nextId();

        leaderboardEntryRepository.saveAndFlush(
                LeaderboardEntry.builder()
                        .userId(userId)
                        .username("overflow-user")
                        .points(Long.MAX_VALUE)
                        .build()
        );

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
                """.formatted(eventId, nextId(), userId);

        sendEvent(eventJson);

        String dltMessage = awaitDltEvent(eventId);

        assertThat(dltMessage).contains("\"eventId\":" + eventId);

        verify(leaderboardService, times(3))
                .processLeaderboardEvent(argThat(
                        event -> event != null
                                && event.eventId() != null
                                && event.eventId() == eventId
                ));

        LeaderboardEntry entry = leaderboardEntryRepository
                .findByUserId(userId)
                .orElseThrow();

        assertThat(entry.getPoints()).isEqualTo(Long.MAX_VALUE);

        assertThat(
                processedEventRepository.existsByEventIdAndEventTypeAndActionType(
                        eventId,
                        EventType.POST,
                        ActionType.CREATED
                )
        ).isFalse();

        assertThat(redisSyncOutboxRepository.findAll())
                .noneMatch(task -> task.getUserId().equals(userId));
    }

    private long nextId() {
        return EVENT_IDS.incrementAndGet();
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

    private String awaitDltEvent(long eventId) {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-test-" + UUID.randomUUID()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(properties)) {

            consumer.subscribe(List.of(DLT_TOPIC));

            AtomicReference<String> matchingMessage =
                    new AtomicReference<>();

            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> {
                        ConsumerRecords<String, String> records =
                                consumer.poll(Duration.ofMillis(250));

                        for (ConsumerRecord<String, String> record : records) {
                            String value = record.value();

                            if (value != null
                                    && compactJson(value).contains(
                                    "\"eventId\":" + eventId
                            )) {
                                matchingMessage.set(value);
                                return true;
                            }
                        }

                        return false;
                    });

            return compactJson(matchingMessage.get());
        }
    }

    private String compactJson(String json) {
        return json.replaceAll("\\s+", "");
    }
}
