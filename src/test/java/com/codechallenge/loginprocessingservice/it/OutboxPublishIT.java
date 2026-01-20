package com.codechallenge.loginprocessingservice.it;

import com.codechallenge.loginprocessingservice.AbstractTest;
import com.codechallenge.loginprocessingservice.config.KafkaTestProducerConfig;
import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;
import com.codechallenge.loginprocessingservice.model.PublicationStatus;
import com.codechallenge.loginprocessingservice.repository.LoginTrackingResultRepository;
import com.codechallenge.loginprocessingservice.repository.OutboxRepository;
import com.codechallenge.loginprocessingservice.service.OutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@Import(KafkaTestProducerConfig.class)
public class OutboxPublishIT extends AbstractTest {

    @Autowired
    private KafkaTemplate<String, CustomerLoginEvent> customerLoginKafkaTemplate;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginTrackingResultRepository resultRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        configureFor(wireMockContainer.getHost(), wireMockContainer.getFirstMappedPort());
        outboxRepository.deleteAll();
        resultRepository.deleteAll();
    }

    @Test
    void shouldPublishOutboxAndMarkSent_andMessageShouldExistOnOutputTopic() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                .willReturn(aResponse().withStatus(204)));

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "Samira", "web", Instant.now(), messageId, "10.0.0.1"
        );

        customerLoginKafkaTemplate.send("customer-login", customerId.toString(), in);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertEquals(1L, outboxRepository.count());
            assertEquals(PublicationStatus.NEW, outboxRepository.findAll().get(0).getStatus());
        });

        outboxPublisher.publishBatch();

        await().atMost(15, SECONDS).untilAsserted(() -> {
            var row = outboxRepository.findAll().get(0);
            assertEquals(PublicationStatus.SENT, row.getStatus());
            assertNotNull(row.getSentAt());
        });

        LoginTrackingResultEvent produced = pollOutputEvent("login-tracking-result", customerId.toString());
        assertNotNull(produced);

        assertEquals(customerId, produced.customerId());
        assertEquals(messageId, produced.messageId());
        assertEquals("web", produced.client());
        assertEquals("Samira", produced.username());
        assertNotNull(produced.timestamp());
        assertNotNull(produced.requestResult());
    }

    private LoginTrackingResultEvent pollOutputEvent(String topic, String expectedKey) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-output-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                var records = consumer.poll(Duration.ofMillis(500));
                for (var r : records) {
                    if (expectedKey.equals(r.key())) {
                        return objectMapper.readValue(r.value(), LoginTrackingResultEvent.class);
                    }
                }
            }
        }
        return null;
    }
}
