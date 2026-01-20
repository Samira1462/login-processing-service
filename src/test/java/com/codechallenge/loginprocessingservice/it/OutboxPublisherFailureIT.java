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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@TestPropertySource(properties = {
        "app.outbox.max-retries=2",
        "app.outbox.batch-size=50"
})
public class OutboxPublisherFailureIT extends AbstractTest {

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
    void shouldIncreaseRetryCount_andMarkFailedAfterMaxRetries() {

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


        CompletableFuture failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom"));

        Mockito.doReturn(failed)
                .when(outboxKafkaTemplate)
                .send(anyString(), anyString(), any(byte[].class));


        outboxPublisher.publishBatch();

        OutboxEntity after1 = outboxRepository.findById(outboxId).orElseThrow();
        assertEquals(PublicationStatus.NEW, after1.getStatus(), "After the first failure, it should still be NEW.");
        assertEquals(1, after1.getRetryCount());
        assertNotNull(after1.getLastAttemptAt());
        assertNotNull(after1.getLastError());
        assertNull(after1.getSentAt());


        outboxPublisher.publishBatch();

        OutboxEntity after2 = outboxRepository.findById(outboxId).orElseThrow();
        assertEquals(PublicationStatus.FAILED, after2.getStatus(), "After reaching maxRetries, it should be marked as FAILED.");
        assertEquals(2, after2.getRetryCount());
        assertNotNull(after2.getLastAttemptAt());
        assertNotNull(after2.getLastError());
        assertNull(after2.getSentAt());

        Mockito.verify(outboxKafkaTemplate, Mockito.times(2))
                .send(eq("login-tracking-result"), eq(customerId.toString()), any(byte[].class));
    }
}
