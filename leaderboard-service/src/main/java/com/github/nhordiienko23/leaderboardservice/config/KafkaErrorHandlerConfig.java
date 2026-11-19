package com.github.nhordiienko23.leaderboardservice.config;

import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardcontract.event.UserEvent;
import com.github.nhordiienko23.leaderboardservice.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaErrorHandlerConfig {

    private static final String TRUSTED_PACKAGE =
            "com.github.nhordiienko23.leaderboardcontract.event";

    @Bean
    public ProducerFactory<Object, Object> kafkaProducerFactory(
            KafkaProperties kafkaProperties
    ) {
        Map<String, Object> properties =
                kafkaProperties.buildProducerProperties();

        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
        serializers.put(byte[].class, new ByteArraySerializer());
        serializers.put(String.class, new StringSerializer());
        serializers.put(Object.class, new JacksonJsonSerializer<>());

        return new DefaultKafkaProducerFactory<>(
                properties,
                null,
                new DelegatingByTypeSerializer(serializers, true)
        );
    }

    @Bean
    public KafkaTemplate<Object, Object> kafkaTemplate(
            ProducerFactory<Object, Object> kafkaProducerFactory
    ) {
        return new KafkaTemplate<>(kafkaProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, LeaderboardEvent> leaderboardConsumerFactory(
            KafkaProperties kafkaProperties
    ) {
        Map<String, Object> properties =
                kafkaProperties.buildConsumerProperties();

        properties.remove(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        properties.remove("spring.json.value.default.type");

        Deserializer<LeaderboardEvent> deserializer =
                new ErrorHandlingDeserializer<>(
                        new JsonDeserializer<>(
                                LeaderboardEvent.class,
                                false
                        ).trustedPackages(TRUSTED_PACKAGE)
                );

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConsumerFactory<String, UserEvent> userConsumerFactory(
            KafkaProperties kafkaProperties
    ) {
        Map<String, Object> properties =
                kafkaProperties.buildConsumerProperties();

        properties.remove(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        properties.remove("spring.json.value.default.type");

        Deserializer<UserEvent> deserializer =
                new ErrorHandlingDeserializer<>(
                        new JsonDeserializer<>(
                                UserEvent.class,
                                false
                        ).trustedPackages(TRUSTED_PACKAGE)
                );

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LeaderboardEvent>
    leaderboardKafkaListenerContainerFactory(
            ConsumerFactory<String, LeaderboardEvent> leaderboardConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, LeaderboardEvent>();

        factory.setConsumerFactory(leaderboardConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserEvent>
    userKafkaListenerContainerFactory(
            ConsumerFactory<String, UserEvent> userConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, UserEvent>();

        factory.setConsumerFactory(userConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
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