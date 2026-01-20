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

public class OutboxPublisherErrorTruncationIT extends AbstractTest {

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
    void shouldTruncateLastErrorTo2000Chars_whenExceptionMessageIsTooLong() {
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

        String longMsg = "X".repeat(2500);

        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException(longMsg));

        Mockito.doReturn((CompletableFuture) failed)
                .when(outboxKafkaTemplate)
                .send(anyString(), anyString(), any(byte[].class));

        outboxPublisher.publishBatch();

        OutboxEntity after = outboxRepository.findById(outboxId).orElseThrow();
        assertEquals(1, after.getRetryCount());
        assertNotNull(after.getLastError());

        assertEquals(2000, after.getLastError().length(), "lastError should be truncated to exactly 2000 characters.");
        assertEquals(longMsg.substring(0, 2000), after.getLastError());

        Mockito.verify(outboxKafkaTemplate, Mockito.times(1))
                .send(eq("login-tracking-result"), eq(customerId.toString()), any(byte[].class));
    }
}
