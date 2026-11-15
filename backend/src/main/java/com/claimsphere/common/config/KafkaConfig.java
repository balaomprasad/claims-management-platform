package com.claimsphere.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka topology: the claim events topic, its dead-letter topic and the consumer error handler
 * (3 attempts in total, then the record is published to {@code <topic>.DLT}).
 */
@Configuration
public class KafkaConfig {

    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long MAX_RETRIES = 2L;

    @Bean
    public NewTopic claimEventsTopic(@Value("${app.kafka.claim-events-topic}") String topic,
                                     @Value("${app.kafka.partitions}") int partitions,
                                     @Value("${app.kafka.replicas}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic claimEventsDeadLetterTopic(@Value("${app.kafka.claim-events-topic}") String topic,
                                               @Value("${app.kafka.partitions}") int partitions,
                                               @Value("${app.kafka.replicas}") int replicas) {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }
}
