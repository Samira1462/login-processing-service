package com.codechallenge.loginprocessingservice.it;

import com.codechallenge.loginprocessingservice.AbstractTest;
import com.codechallenge.loginprocessingservice.model.*;
import com.codechallenge.loginprocessingservice.repository.OutboxRepository;
import com.codechallenge.loginprocessingservice.service.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@TestPropertySource(properties = {
        "app.outbox.max-retries=1",
        "app.outbox.batch-size=50"
})
public class OutboxPublisherSkipFailedIT extends AbstractTest {

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxRepository outboxRepository;

    @MockitoSpyBean
    private KafkaTemplate<String, byte[]> outboxKafkaTemplate;

    @BeforeEach
    void clean() {
        outboxRepository.deleteAll();
    }

    @Test
    void shouldNotPickFailedEventsOnNextRun_becauseQueryOnlySelectsNEW() {
        UUID aggregateId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        OutboxEntity e = OutboxEntity.newEvent(
                AggregateType.LOGIN_TRACKING_RESULT,
                aggregateId,
                IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED,
                "login-tracking-result",
                customerId.toString(),
                "dummy-payload".getBytes()
        );
        e = outboxRepository.save(e);
        UUID outboxId = e.getId();

        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom"));
        Mockito.doReturn(failed)
                .when(outboxKafkaTemplate)
                .send(anyString(), anyString(), any(byte[].class));

        outboxPublisher.publishBatch();

        OutboxEntity after1 = outboxRepository.findById(outboxId).orElseThrow();
        assertEquals(PublicationStatus.FAILED, after1.getStatus());
        assertEquals(1, after1.getRetryCount());
        assertNotNull(after1.getLastAttemptAt());
        Instant attempt1 = after1.getLastAttemptAt();

        outboxPublisher.publishBatch();

        OutboxEntity after2 = outboxRepository.findById(outboxId).orElseThrow();
        assertEquals(PublicationStatus.FAILED, after2.getStatus());
        assertEquals(1, after2.getRetryCount());
        assertEquals(attempt1, after2.getLastAttemptAt(), "No retry should have been attempted");

        Mockito.verify(outboxKafkaTemplate, Mockito.times(1))
                .send(eq("login-tracking-result"), eq(customerId.toString()), any(byte[].class));
    }
}
