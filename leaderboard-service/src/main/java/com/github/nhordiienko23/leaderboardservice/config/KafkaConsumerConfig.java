
package com.github.nhordiienko23.leaderboardservice.config;

import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardcontract.event.UserEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, LeaderboardEvent> leaderboardEventConsumerFactory(
            KafkaProperties properties
    ) {
        JsonDeserializer<LeaderboardEvent> deserializer =
                new JsonDeserializer<>(LeaderboardEvent.class, false);

        deserializer.addTrustedPackages(
                "com.github.nhordiienko23.leaderboardcontract.event"
        );

        return new DefaultKafkaConsumerFactory<>(
                properties.buildConsumerProperties(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConsumerFactory<String, UserEvent> userEventConsumerFactory(
            KafkaProperties properties
    ) {
        JsonDeserializer<UserEvent> deserializer =
                new JsonDeserializer<>(UserEvent.class, false);

        deserializer.addTrustedPackages(
                "com.github.nhordiienko23.leaderboardcontract.event"
        );

        return new DefaultKafkaConsumerFactory<>(
                properties.buildConsumerProperties(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LeaderboardEvent>
    leaderboardEventKafkaListenerContainerFactory(
            ConsumerFactory<String, LeaderboardEvent> leaderboardEventConsumerFactory
    ) {
        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, LeaderboardEvent>();

        factory.setConsumerFactory(leaderboardEventConsumerFactory);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserEvent>
    userEventKafkaListenerContainerFactory(
            ConsumerFactory<String, UserEvent> userEventConsumerFactory
    ) {
        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, UserEvent>();

        factory.setConsumerFactory(userEventConsumerFactory);
        return factory;
    }
}
