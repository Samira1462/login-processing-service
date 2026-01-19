package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;
import com.codechallenge.loginprocessingservice.integration.CustomerTrackingClient;
import com.codechallenge.loginprocessingservice.persistence.model.*;
import com.codechallenge.loginprocessingservice.persistence.repository.LoginTrackingResultRepository;
import com.codechallenge.loginprocessingservice.persistence.repository.OutboxEventRepository;
import io.github.resilience4j.retry.Retry;

import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

import static com.codechallenge.loginprocessingservice.mapper.ClientMapper.toClient;
import static com.codechallenge.loginprocessingservice.mapper.LoginTrackingResultMapper.toEvent;

@Service
public class LoginProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(LoginProcessingService.class);

    private final CustomerTrackingClient customerTrackingClient;
    private final LoginTrackingResultRepository resultRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxPayloadSerializer payloadSerializer;

    private final String outputTopic;
    private final Retry customerTrackingRetry;

    public LoginProcessingService(CustomerTrackingClient customerTrackingClient,
                                  LoginTrackingResultRepository resultRepository,
                                  OutboxEventRepository outboxRepository,
                                  OutboxPayloadSerializer payloadSerializer,
                                  RetryRegistry retryRegistry,
                                  @Value("${app.kafka.topic.output}") String outputTopic) {
        this.customerTrackingClient = customerTrackingClient;
        this.resultRepository = resultRepository;
        this.outboxRepository = outboxRepository;
        this.payloadSerializer = payloadSerializer;
        this.outputTopic = outputTopic;

        this.customerTrackingRetry = retryRegistry.retry("customerTracking");
    }

    @Transactional
    public LoginTrackingResultEvent process(CustomerLoginEvent event) {
        logger.info("Processing login event messageId={} customerId={}", event.messageId(), event.customerId());

        var existing = resultRepository.findByMessageId(event.messageId());
        if (existing.isPresent()) {
            logger.info("Duplicate message detected. Skipping processing. messageId={}", event.messageId());
            return toEvent(existing.get());
        }

        RequestResult requestResult = executeCustomerTrackingService(event);

        LoginTrackingResultEntity saved = persistResult(event, requestResult);

        writeOutbox(saved);

        return toEvent(saved);
    }

    private RequestResult executeCustomerTrackingService(CustomerLoginEvent event) {
        Supplier<Boolean> call = () -> customerTrackingClient.notifyLogin(event.customerId());
        Supplier<Boolean> decorated = Retry.decorateSupplier(customerTrackingRetry, call);

        try {
            boolean ok = decorated.get();
            return ok ? RequestResult.SUCCESSFUL : RequestResult.UNSUCCESSFUL;
        } catch (Exception ex) {
            logger.warn("Tracking failed after retries. customerId={} messageId={}",
                    event.customerId(), event.messageId(), ex);
            return RequestResult.UNSUCCESSFUL;
        }
    }

    private LoginTrackingResultEntity persistResult(CustomerLoginEvent event, RequestResult requestResult) {
        try {
            LoginTrackingResultEntity entity = LoginTrackingResultEntity.of(
                    event.messageId(),
                    event.customerId(),
                    event.username(),
                    toClient(event.client()),
                    event.timestamp(),
                    event.customerIp(),
                    requestResult
            );

            return resultRepository.save(entity);

        } catch (DataIntegrityViolationException dup) {
            logger.info("Duplicate message detected (DB constraint). messageId={}", event.messageId());

            return resultRepository.findByMessageId(event.messageId())
                    .orElseThrow(() -> dup);
        }
    }

    private void writeOutbox(LoginTrackingResultEntity saved) {
        LoginTrackingResultEvent outEvent = toEvent(saved);
        byte[] payload = payloadSerializer.serialize(outEvent);

        OutboxEventEntity outbox = OutboxEventEntity.newEvent(
                AggregateType.LOGIN_TRACKING_RESULT,
                saved.getId(),
                OutboxEventType.LOGIN_TRACKING_RESULT_CREATED,
                outputTopic,
                saved.getCustomerId().toString(), // key
                payload
        );

        try {
            outboxRepository.save(outbox);
        } catch (DataIntegrityViolationException dup) {
            logger.info("Outbox duplicate ignored. aggregateId={} eventType={}",
                    saved.getId(), OutboxEventType.LOGIN_TRACKING_RESULT_CREATED);
        }
    }

}
