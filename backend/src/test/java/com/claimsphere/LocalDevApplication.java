package com.claimsphere;

import org.h2.tools.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.sql.SQLException;

/**
 * Runs the full application without Docker: in-memory H2 (PostgreSQL mode) plus an in-JVM Kafka broker.
 * Development aid only — lives on the test classpath and is never packaged.
 * <p>
 * Also starts the H2 web console on http://localhost:8082 (localhost only) so the in-memory database can be
 * inspected; connect with the JDBC URL from {@code application-local-dev.yml}, user {@code sa}, empty password.
 * <p>
 * Usage: {@code JWT_SECRET=... DEMO_PASSWORD=... ./mvnw spring-boot:test-run}
 */
public final class LocalDevApplication {

    private static final String H2_CONSOLE_PORT = "8082";

    private LocalDevApplication() {
    }

    public static void main(String[] args) throws SQLException {
        Server console = Server.createWebServer("-webPort", H2_CONSOLE_PORT).start();
        Runtime.getRuntime().addShutdownHook(new Thread(console::stop));

        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 1);
        broker.afterPropertiesSet();
        Runtime.getRuntime().addShutdownHook(new Thread(broker::destroy));
        System.setProperty("spring.kafka.bootstrap-servers", broker.getBrokersAsString());

        SpringApplication.from(ClaimSphereApplication::main)
                .withAdditionalProfiles("local-dev", "demo")
                .run(args);
    }
}
