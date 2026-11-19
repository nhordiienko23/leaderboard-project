package com.github.nhordiienko23.userservice.kafka;

import com.github.nhordiienko23.userservice.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic postCreatedTopic() {
        return new NewTopic(
                KafkaTopics.USER_EVENTS,
                1,
                (short) 1
        );
    }
}
