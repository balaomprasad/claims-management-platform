package com.claimsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/**
 * Runs the full application without Docker: in-memory H2 (PostgreSQL mode) plus an in-JVM Kafka broker.
 * Development aid only — lives on the test classpath and is never packaged.
 * <p>
 * Usage: {@code JWT_SECRET=... DEMO_PASSWORD=... ./mvnw spring-boot:test-run}
 */
public final class LocalDevApplication {

    private LocalDevApplication() {
    }

    public static void main(String[] args) {
        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 1);
        broker.afterPropertiesSet();
        Runtime.getRuntime().addShutdownHook(new Thread(broker::destroy));
        System.setProperty("spring.kafka.bootstrap-servers", broker.getBrokersAsString());

        SpringApplication.from(ClaimSphereApplication::main)
                .withAdditionalProfiles("local-dev", "demo")
                .run(args);
    }
}
