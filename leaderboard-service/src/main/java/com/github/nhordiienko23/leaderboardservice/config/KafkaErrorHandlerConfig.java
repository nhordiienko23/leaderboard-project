package com.github.nhordiienko23.leaderboardservice.config;

import com.github.nhordiienko23.leaderboardservice.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public ProducerFactory<Object, Object> kafkaProducerFactory(
            KafkaProperties kafkaProperties
    ) {
        Map<String, Object> producerProperties =
                kafkaProperties.buildProducerProperties();

        producerProperties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        Map<Class<?>, Serializer<?>> valueSerializers =
                new LinkedHashMap<>();

        valueSerializers.put(
                byte[].class,
                new ByteArraySerializer()
        );

        valueSerializers.put(
                String.class,
                new StringSerializer()
        );

    
        valueSerializers.put(
                Object.class,
                new JacksonJsonSerializer<>()
        );

        return new DefaultKafkaProducerFactory<>(
                producerProperties,
                null,
                new DelegatingByTypeSerializer(valueSerializers, true)
        );
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, _) ->
                                new TopicPartition(
                                        record.topic() + "-dlt",
                                        -1
                                )
                );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1_000L, 2L)
        );

        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class
        );

        return errorHandler;
    }

    @Bean
    public NewTopic leaderboardEventsDltTopic() {
        return dltTopic(KafkaTopics.LEADERBOARD_EVENTS);
    }

    @Bean
    public NewTopic userEventsDltTopic() {
        return dltTopic(KafkaTopics.USER_EVENTS);
    }

    private NewTopic dltTopic(String sourceTopic) {
        return TopicBuilder.name(sourceTopic + "-dlt")
                .partitions(1)
                .replicas(1)
                .build();
    }
}