package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;
import com.codechallenge.loginprocessingservice.adapter.CustomerTrackingClient;
import com.codechallenge.loginprocessingservice.model.*;
import com.codechallenge.loginprocessingservice.repository.LoginTrackingResultRepository;
import com.codechallenge.loginprocessingservice.repository.OutboxRepository;
import io.github.resilience4j.retry.Retry;

import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Supplier;

import static com.codechallenge.loginprocessingservice.mapper.LoginTrackingResultMapper.toEvent;

@Service
public class LoginProcessingServiceImpl implements LoginProcessingService{

    private static final Logger logger = LoggerFactory.getLogger(LoginProcessingServiceImpl.class);

    private final CustomerTrackingClient customerTrackingClient;
    private final LoginTrackingResultRepository resultRepository;
    private final OutboxRepository outboxRepository;
    private final IntegrationEventSerializer payloadSerializer;

    private final String outputTopic;
    private final Retry customerTrackingRetry;

    public LoginProcessingServiceImpl(CustomerTrackingClient customerTrackingClient,
                                      LoginTrackingResultRepository resultRepository,
                                      OutboxRepository outboxRepository,
                                      IntegrationEventSerializer payloadSerializer,
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
        logger.info("[DEBUG_LOG] Processing login event messageId={} customerId={}", event.messageId(), event.customerId());

        var existing = resultRepository.findByMessageId(event.messageId());
        if (existing.isPresent()) {
            logger.info("[DEBUG_LOG] Duplicate message detected. Skipping processing. messageId={}", event.messageId());
            return toEvent(existing.get());
        }

        RequestResult requestResult = executeCustomerTrackingService(event);
        logger.info("[DEBUG_LOG] RequestResult for messageId={}: {}", event.messageId(), requestResult);


        LoginTrackingResultEntity saved = persistResult(event, requestResult);
        logger.info("[DEBUG_LOG] Saved entity for messageId={} with id={}", event.messageId(), saved.getId());

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

/*    private LoginTrackingResultEntity persistResult(CustomerLoginEvent event, RequestResult requestResult) {
        try {
            LoginTrackingResultEntity entity = toEntity(event, requestResult);

            return resultRepository.save(entity);

        } catch (DataIntegrityViolationException dup) {
            logger.info("Duplicate message detected (DB constraint). messageId={}", event.messageId());

            return resultRepository.findByMessageId(event.messageId())
                    .orElseThrow(() -> dup);
        }
    }*/

    private LoginTrackingResultEntity persistResult(CustomerLoginEvent event, RequestResult requestResult) {

        String client = Client.fromString(event.client()).name();

        resultRepository.insertIgnore(
                UUID.randomUUID(),
                event.messageId(),
                event.customerId(),
                event.username(),
                client,
                event.timestamp(),
                event.customerIp(),
                requestResult.name()
        );

        return resultRepository.findByMessageId(event.messageId()).orElseThrow();
    }

    private void writeOutbox(LoginTrackingResultEntity saved) {
        LoginTrackingResultEvent outEvent = toEvent(saved);
        byte[] payload = payloadSerializer.serialize(outEvent);

        outboxRepository.insertIgnore(
                UUID.randomUUID(),
                AggregateType.LOGIN_TRACKING_RESULT.name(),
                saved.getId(),
                IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED.name(),
                outputTopic,
                saved.getCustomerId().toString(),
                payload
        );
    }
/*    private void writeOutbox(LoginTrackingResultEntity saved) {
        LoginTrackingResultEvent outEvent = toEvent(saved);
        byte[] payload = payloadSerializer.serialize(outEvent);

        OutboxEntity outbox = OutboxEntity.newEvent(
                AggregateType.LOGIN_TRACKING_RESULT,
                saved.getId(),
                IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED,
                outputTopic,
                saved.getCustomerId().toString(), // key
                payload
        );

        try {
            outboxRepository.save(outbox);
        } catch (DataIntegrityViolationException dup) {
            logger.info("Outbox duplicate ignored. aggregateId={} eventType={}",
                    saved.getId(), IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED);
        }
    }*/

}
