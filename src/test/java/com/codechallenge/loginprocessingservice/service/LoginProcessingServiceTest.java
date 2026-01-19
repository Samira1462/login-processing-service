package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.integration.CustomerTrackingClient;
import com.codechallenge.loginprocessingservice.model.*;
import com.codechallenge.loginprocessingservice.repository.LoginTrackingResultRepository;
import com.codechallenge.loginprocessingservice.repository.OutboxEventRepository;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.codechallenge.loginprocessingservice.mapper.LoginTrackingResultMapper.toEntity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginProcessingServiceTest {

    @Mock CustomerTrackingClient customerTrackingClient;
    @Mock LoginTrackingResultRepository resultRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Mock OutboxPayloadSerializer payloadSerializer;
    @Mock
    RetryRegistry retryRegistry;

    LoginProcessingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        Retry testRetry = Retry.of("customerTracking-test", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ZERO)
                .retryExceptions(RestClientException.class)
                .build());

        when(retryRegistry.retry("customerTracking")).thenReturn(testRetry);

        service = new LoginProcessingService(
                customerTrackingClient,
                resultRepository,
                outboxRepository,
                payloadSerializer,
                retryRegistry,
                "login-tracking-result"
        );
    }

    @Test
    void processWhenDuplicateMessage_shouldReturnExistingAndNotCallRest() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.parse("2026-01-18T10:00:00Z");

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "samira", "web", ts, messageId, "10.0.0.1"
        );

        LoginTrackingResultEntity existing = toEntity(in, RequestResult.SUCCESSFUL);

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.of(existing));

        var out = service.process(in);

        assertEquals(RequestResult.SUCCESSFUL, out.requestResult());
        assertEquals(messageId, out.messageId());

        verifyNoInteractions(customerTrackingClient);
    }

    @Test
    void processWhenNewMessage_andRestSucceedsOnFirstTry_shouldReturnSuccessfulAndCallRestOnce() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "bob", "android", ts, messageId, "10.0.0.2"
        );

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.empty());
        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);
        when(resultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var out = service.process(in);

        assertEquals(RequestResult.SUCCESSFUL, out.requestResult());
        verify(customerTrackingClient, times(1)).notifyLogin(customerId);
    }

    @Test
    void processWhenNewMessage_andRestFailsThenSucceeds_shouldRetryAndReturnSuccessful() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "carol", "ios", ts, messageId, "10.0.0.3"
        );

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.empty());
        when(resultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(customerTrackingClient.notifyLogin(customerId))
                .thenThrow(new RestClientException("boom1"))
                .thenThrow(new RestClientException("boom2"))
                .thenReturn(true);

        var out = service.process(in);

        assertEquals(RequestResult.SUCCESSFUL, out.requestResult());
        verify(customerTrackingClient, times(3)).notifyLogin(customerId);
    }

    @Test
    void processWhenNewMessage_andRestAlwaysFails_shouldRetry3TimesAndReturnUnsuccessful() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "dave", "web", ts, messageId, "10.0.0.4"
        );

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.empty());
        when(resultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(customerTrackingClient.notifyLogin(customerId))
                .thenThrow(new RestClientException("fail1"))
                .thenThrow(new RestClientException("fail2"))
                .thenThrow(new RestClientException("fail3"));

        var out = service.process(in);

        assertEquals(RequestResult.UNSUCCESSFUL, out.requestResult());
        verify(customerTrackingClient, times(3)).notifyLogin(customerId);
    }

    @Test
    void process_whenNewMessage_shouldPersistResultEntity() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(customerId, "u", "web", ts, messageId, "10.0.0.1");

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.empty());
        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);

        when(resultRepository.save(any(LoginTrackingResultEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var out = service.process(in);

        assertEquals(RequestResult.SUCCESSFUL, out.requestResult());
        verify(resultRepository, times(1)).save(any(LoginTrackingResultEntity.class));
    }

    @Test
    void process_whenSaveHitsUniqueConstraint_shouldLoadExistingAndReturnIt() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(customerId, "u", "web", ts, messageId, "10.0.0.1");

        LoginTrackingResultEntity existing = toEntity(in, RequestResult.SUCCESSFUL);

        when(resultRepository.findByMessageId(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);

        when(resultRepository.save(any(LoginTrackingResultEntity.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        var out = service.process(in);

        assertEquals(messageId, out.messageId());
        assertEquals(RequestResult.SUCCESSFUL, out.requestResult());

        verify(resultRepository, times(1)).save(any(LoginTrackingResultEntity.class));
        verify(resultRepository, times(2)).findByMessageId(messageId);
    }

    @Test
    void process_whenClientInvalid_shouldThrowIllegalArgumentException() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(customerId, "u", "windows-phone", ts, messageId, "10.0.0.1");

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.empty());
        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.process(in));
    }

    @Test
    void process_whenNewMessage_shouldPersistResult_andWriteOutbox_andReturnEvent() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.parse("2026-01-18T10:00:00Z");

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId,
                "alice",
                "web",
                ts,
                messageId,
                "10.0.0.1"
        );

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.empty());

        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);

        when(resultRepository.save(any(LoginTrackingResultEntity.class))).thenAnswer(inv -> {
            LoginTrackingResultEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        byte[] payload = "{\"ok\":true}".getBytes();
        when(payloadSerializer.serialize(any())).thenReturn(payload);
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var out = service.process(in);

        assertEquals(customerId, out.customerId());
        assertEquals(messageId, out.messageId());
        assertEquals(RequestResult.SUCCESSFUL, out.requestResult());

        verify(customerTrackingClient, times(1)).notifyLogin(customerId);

        ArgumentCaptor<LoginTrackingResultEntity> resultCaptor = ArgumentCaptor.forClass(LoginTrackingResultEntity.class);
        verify(resultRepository, times(1)).save(resultCaptor.capture());
        LoginTrackingResultEntity savedEntity = resultCaptor.getValue();

        assertEquals(messageId, savedEntity.getMessageId());
        assertEquals(customerId, savedEntity.getCustomerId());
        assertEquals("alice", savedEntity.getUsername());
        assertEquals(Client.WEB, savedEntity.getClient());
        assertEquals(ts, savedEntity.getEventTimestamp());
        assertEquals("10.0.0.1", savedEntity.getCustomerIp());
        assertEquals(RequestResult.SUCCESSFUL, savedEntity.getRequestResult());

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository, times(1)).save(outboxCaptor.capture());
        OutboxEventEntity ob = outboxCaptor.getValue();

        assertEquals(AggregateType.LOGIN_TRACKING_RESULT, ob.getAggregateType());
        assertNotNull(savedEntity.getId());
        assertEquals(savedEntity.getId(), ob.getAggregateId());

        assertEquals(OutboxEventType.LOGIN_TRACKING_RESULT_CREATED, ob.getEventType());
        assertEquals("login-tracking-result", ob.getTopic());
        assertEquals(customerId.toString(), ob.getKey());
        assertArrayEquals(payload, ob.getPayload());
        assertEquals(OutboxStatus.NEW, ob.getStatus());
        assertEquals(0, ob.getRetryCount());
    }

    @Test
    void process_whenOutboxDuplicate_shouldNotFail_andStillReturnEvent() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId,
                "bob",
                "android",
                ts,
                messageId,
                "10.0.0.2"
        );

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.empty());
        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);

        when(resultRepository.save(any(LoginTrackingResultEntity.class))).thenAnswer(inv -> {
            LoginTrackingResultEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        when(payloadSerializer.serialize(any())).thenReturn("{}".getBytes());

        when(outboxRepository.save(any(OutboxEventEntity.class)))
                .thenThrow(new DataIntegrityViolationException("dup outbox"));

        assertDoesNotThrow(() -> {
            var out = service.process(in);
            assertEquals(RequestResult.SUCCESSFUL, out.requestResult());
        });

        verify(resultRepository, times(1)).save(any(LoginTrackingResultEntity.class));
        verify(outboxRepository, times(1)).save(any(OutboxEventEntity.class));
    }

    @Test
    void process_whenRestAlwaysFails_shouldRetry3Times_andPersistUnsuccessful_andWriteOutbox() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId,
                "carol",
                "ios",
                ts,
                messageId,
                "10.0.0.3"
        );

        when(resultRepository.findByMessageId(messageId)).thenReturn(Optional.empty());

        when(customerTrackingClient.notifyLogin(customerId))
                .thenThrow(new RestClientException("fail1"))
                .thenThrow(new RestClientException("fail2"))
                .thenThrow(new RestClientException("fail3"));

        when(resultRepository.save(any(LoginTrackingResultEntity.class))).thenAnswer(inv -> {
            LoginTrackingResultEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        when(payloadSerializer.serialize(any())).thenReturn("{}".getBytes());
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var out = service.process(in);

        assertEquals(RequestResult.UNSUCCESSFUL, out.requestResult());
        verify(customerTrackingClient, times(3)).notifyLogin(customerId);

        ArgumentCaptor<LoginTrackingResultEntity> entityCaptor = ArgumentCaptor.forClass(LoginTrackingResultEntity.class);
        verify(resultRepository).save(entityCaptor.capture());
        assertEquals(RequestResult.UNSUCCESSFUL, entityCaptor.getValue().getRequestResult());

        verify(outboxRepository, times(1)).save(any(OutboxEventEntity.class));
    }



}