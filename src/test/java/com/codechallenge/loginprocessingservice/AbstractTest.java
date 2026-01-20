package com.codechallenge.loginprocessingservice;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers
public abstract class AbstractTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTest.class.getName());

    @Container
    protected static final WireMockContainer wireMockContainer =
            new WireMockContainer("wiremock/wiremock:3.6.0");

    @Container
    protected static final KafkaContainer kafkaContainer =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @Container
    protected static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("login_processing_db")
                    .withUsername("login_processing_user")
                    .withPassword("login_processing_password");

    @BeforeAll
    static void containersAreUp() {
        assertTrue(wireMockContainer.isRunning(), "WireMockContainer is not running");
        assertTrue(kafkaContainer.isRunning(), "KafkaContainer is not running");
        assertTrue(postgresContainer.isRunning(), "PostgresContainer is not running");

        logger.info("WireMock:  {}", wireMockContainer.getBaseUrl());
        logger.info("Kafka:     {}", kafkaContainer.getBootstrapServers());
        logger.info("Postgres:  {}", postgresContainer.getJdbcUrl());
    }

    @DynamicPropertySource
    static void dynamicProps(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");

        registry.add("app.customer-tracking.base-url", wireMockContainer::getBaseUrl);
        registry.add("app.customer-tracking.username", () -> "tracking_user");
        registry.add("app.customer-tracking.password", () -> "");

        registry.add("app.kafka.topic.input", () -> "customer-login");
        registry.add("app.kafka.topic.output", () -> "login-tracking-result");

        registry.add("app.outbox.poll-ms", () -> "999999");

        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }
}