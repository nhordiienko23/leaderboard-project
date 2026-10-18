package com.github.nhordiienko23.postservice.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KafkaTopicConfigTest {

    private KafkaTopicConfig kafkaTopicConfig;

    @BeforeEach
    void setUp() {
        kafkaTopicConfig = new KafkaTopicConfig();
    }

    @Test
    void postCreatedTopic_shouldCreateTopicWithCorrectName() {
        NewTopic topic = kafkaTopicConfig.postCreatedTopic();

        assertNotNull(topic);
        assertEquals(KafkaTopics.LEADERBOARD_EVENTS, topic.name());
    }

    @Test
    void postCreatedTopic_shouldConfigureOnePartition() {
        NewTopic topic = kafkaTopicConfig.postCreatedTopic();

        assertEquals(1, topic.numPartitions());
    }

    @Test
    void postCreatedTopic_shouldConfigureReplicationFactorOne() {
        NewTopic topic = kafkaTopicConfig.postCreatedTopic();

        assertEquals((short) 1, topic.replicationFactor());
    }
}