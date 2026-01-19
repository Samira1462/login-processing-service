package com.codechallenge.loginprocessingservice.kafka.outbox;

import com.codechallenge.loginprocessingservice.persistence.model.OutboxEventEntity;
import com.codechallenge.loginprocessingservice.persistence.model.OutboxStatus;
import com.codechallenge.loginprocessingservice.persistence.repository.OutboxEventRepository;
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
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    private final int batchSize;
    private final int maxRetries;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
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
     *
     * Transactional:
     * - status updates are committed even if Kafka send fails for some rows
     * - next poll will pick remaining NEW/FAILED rows based on your strategy
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:500}")
    @Transactional
    public void publishBatch() {
        List<OutboxEventEntity> batch =
                outboxRepository.findByStatusOrderByCreatedAtAsc(
                        OutboxStatus.NEW,
                        PageRequest.of(0, batchSize)
                );

        if (batch.isEmpty()) {
            return;
        }

        log.info("Publishing outbox batch size={}", batch.size());

        for (OutboxEventEntity event : batch) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEventEntity event) {
        try {
            event.setLastAttemptAt(Instant.now());

            kafkaTemplate.send(event.getTopic(), event.getKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);

            event.markSent();

            log.debug("Outbox event sent id={} topic={} key={}", event.getId(), event.getTopic(), event.getKey());

        } catch (Exception ex) {
            String msg = ex.getMessage();
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

