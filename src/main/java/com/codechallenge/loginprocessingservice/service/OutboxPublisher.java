package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.model.OutboxEntity;
import com.codechallenge.loginprocessingservice.model.PublicationStatus;
import com.codechallenge.loginprocessingservice.repository.OutboxRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    private final int batchSize;
    private final int maxRetries;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, byte[]> kafkaTemplate,
                           @Value("${app.outbox.batch-size:50}") int batchSize,
                           @Value("${app.outbox.max-retries:10}") int maxRetries) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    /**
     * Reads NEW outbox events, publishes to Kafka, and updates status.
     * Transactional:
     * - status updates are committed even if Kafka send fails for some rows
     * - next poll will pick remaining NEW/FAILED rows based on your strategy
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:500}")
    @Transactional
    public void publishBatch() {
        List<OutboxEntity> batch =
                outboxRepository.findByStatusOrderByCreatedAtAsc(
                        PublicationStatus.NEW,
                        PageRequest.of(0, batchSize)
                );

        if (batch.isEmpty()) {
            return;
        }

        log.info("Publishing outbox batch size={}", batch.size());

        for (OutboxEntity event : batch) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEntity event) {
        try {
            event.setLastAttemptAt(Instant.now());

            kafkaTemplate.send(event.getTopic(), event.getKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);

            event.markSent();

            log.debug("Outbox event sent id={} topic={} key={}", event.getId(), event.getTopic(), event.getKey());

        } catch (Exception ex) {
            log.error("Error publishing outbox event id={}", event.getId(), ex);
            Throwable cause = (ex instanceof ExecutionException) ? ex.getCause() : ex;
            String msg = cause != null ? cause.getMessage() : ex.getMessage();
            if (msg != null && msg.length() > 2000) {
                msg = msg.substring(0, 2000);
            }

            event.markAttemptFailed(msg);

            if (event.getRetryCount() >= maxRetries) {
                event.markFailedPermanently(msg);
                log.warn("Outbox event permanently failed id={} retries={}", event.getId(), event.getRetryCount(), ex);
            } else {
                log.warn("Outbox publish failed id={} retryCount={}", event.getId(), event.getRetryCount(), ex);
            }

        }
    }
}

