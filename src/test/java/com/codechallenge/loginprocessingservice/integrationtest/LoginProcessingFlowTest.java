package com.codechallenge.loginprocessingservice.integrationtest;

import com.codechallenge.loginprocessingservice.AbstractTest;
import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.persistence.model.OutboxStatus;
import com.codechallenge.loginprocessingservice.persistence.model.RequestResult;
import com.codechallenge.loginprocessingservice.persistence.repository.LoginTrackingResultRepository;
import com.codechallenge.loginprocessingservice.persistence.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

public class LoginProcessingFlowTest extends AbstractTest {

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired LoginTrackingResultRepository resultRepository;
    @Autowired OutboxEventRepository outboxRepository;

    @BeforeEach
    void setUp() {
        configureFor(wireMockContainer.getHost(), wireMockContainer.getFirstMappedPort());
    }

    @Test
    void shouldConsumeCallTrackingPersistResultAndWriteOutbox_whenTrackingReturns2xx() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        stubFor(
                get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                        .willReturn(aResponse().withStatus(204))
        );

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId,
                "Samira",
                "web",
                Instant.now(),
                messageId,
                "10.0.0.1"
        );

        kafkaTemplate.send("customer-login", customerId.toString(), in);

        // چون consumer async است، منتظر می‌مانیم تا DB پر شود
        await().atMost(10, SECONDS).untilAsserted(() -> {
            var saved = resultRepository.findByMessageId(messageId).orElseThrow();
            assertEquals(customerId, saved.getCustomerId());
            assertEquals(RequestResult.SUCCESSFUL, saved.getRequestResult());

            var outboxRows = outboxRepository.findAll();
            assertFalse(outboxRows.isEmpty());
            assertTrue(outboxRows.stream().anyMatch(o ->
                    o.getStatus() == OutboxStatus.NEW &&
                            o.getKey().equals(customerId.toString()) &&
                            o.getTopic().equals("login-tracking-result")
            ));
        });

        verify(1, getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
    }
}