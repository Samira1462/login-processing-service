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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

public class OutboxPublisherRecoveryIT extends AbstractTest {

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
    void shouldFailOnceThenSucceed_andMarkSent() {
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
        failed.completeExceptionally(new RuntimeException("temporary failure"));

        CompletableFuture<Object> success = CompletableFuture.completedFuture(null);

        Mockito.doReturn(failed)
                .doReturn(success)
                .when(outboxKafkaTemplate)
                .send(anyString(), anyString(), any(byte[].class));

        outboxPublisher.publishBatch();

        OutboxEntity after1 = outboxRepository.findById(outboxId).orElseThrow();
        assertEquals(PublicationStatus.NEW, after1.getStatus());
        assertEquals(1, after1.getRetryCount());
        assertNotNull(after1.getLastAttemptAt());
        assertNotNull(after1.getLastError());
        assertNull(after1.getSentAt());

        outboxPublisher.publishBatch();

        OutboxEntity after2 = outboxRepository.findById(outboxId).orElseThrow();
        assertEquals(PublicationStatus.SENT, after2.getStatus());
        assertEquals(1, after2.getRetryCount(), "After success, retryCount should remain unchanged.");
        assertNotNull(after2.getSentAt());
        assertNotNull(after2.getLastAttemptAt());
        assertNull(after2.getLastError(), "After a successful send, lastError should be cleared.");

        Mockito.verify(outboxKafkaTemplate, Mockito.times(2))
                .send(eq("login-tracking-result"), eq(customerId.toString()), any(byte[].class));
    }
}
