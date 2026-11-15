package com.claimsphere.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka topology and producer setup.
 * <ul>
 *   <li>Topics: claim events and their dead-letter topic.</li>
 *   <li>One producer template whose value serializer depends on the payload type: outbox payloads are pre-serialised
 *       JSON strings, dead-lettered records may be raw bytes (undeserialisable input) or objects.</li>
 *   <li>Consumer error handling: 3 attempts in total, then the record is published to {@code <topic>.DLT}.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class KafkaConfig {

    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long MAX_RETRIES = 2L;
    private static final String DEAD_LETTER_SUFFIX = ".DLT";

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
        return TopicBuilder.name(topic + DEAD_LETTER_SUFFIX).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public ProducerFactory<Object, Object> producerFactory(KafkaProperties kafkaProperties, SslBundles sslBundles) {
        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(String.class, new StringSerializer());
        delegates.put(byte[].class, new ByteArraySerializer());
        JsonSerializer<Object> json = new JsonSerializer<>();
        json.setAddTypeInfo(false);
        delegates.put(Object.class, json);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Serializer<Object> keySerializer = (Serializer) new StringSerializer();
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(sslBundles),
                keySerializer, new DelegatingByTypeSerializer(delegates, true));
    }

    @Bean
    public KafkaTemplate<Object, Object> kafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // Explicit destination: the declared "<topic>.DLT", same partition as the failed record.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }
}
