package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.adapter.CustomerTrackingClient;
import com.codechallenge.loginprocessingservice.model.*;
import com.codechallenge.loginprocessingservice.repository.LoginTrackingResultRepository;
import com.codechallenge.loginprocessingservice.repository.OutboxRepository;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.codechallenge.loginprocessingservice.mapper.LoginTrackingResultMapper.toEntity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginProcessingServiceImplTest {

    @Mock
    private CustomerTrackingClient customerTrackingClient;
    @Mock
    private LoginTrackingResultRepository resultRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private IntegrationEventSerializer payloadSerializer;
    @Mock
    private RetryRegistry retryRegistry;

    LoginProcessingServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        Retry testRetry = Retry.of("customerTracking-test", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ZERO)
                .retryExceptions(RestClientException.class)
                .build());

        when(retryRegistry.retry("customerTracking")).thenReturn(testRetry);

        service = new LoginProcessingServiceImpl(
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
                customerId, "Samira", "android", ts, messageId, "10.0.0.2"
        );

        var saved = toEntity(in, RequestResult.SUCCESSFUL);
        saved.setId(UUID.randomUUID());

        when(resultRepository.findByMessageId(messageId))
                .thenReturn(Optional.empty(), Optional.of(saved));

        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);

        when(resultRepository.insertIgnore(
                any(UUID.class),
                eq(messageId),
                eq(customerId),
                eq("Samira"),
                eq(Client.ANDROID.name()),
                eq(ts),
                eq("10.0.0.2"),
                eq(RequestResult.SUCCESSFUL.name())
        )).thenReturn(1);

        byte[] payload = "{}".getBytes();
        when(payloadSerializer.serialize(any())).thenReturn(payload);

        when(outboxRepository.insertIgnore(
                any(UUID.class),
                eq(AggregateType.LOGIN_TRACKING_RESULT.name()),
                eq(saved.getId()),
                eq(IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED.name()),
                eq("login-tracking-result"),
                eq(customerId.toString()),
                eq(payload)
        )).thenReturn(1);

        var out = service.process(in);

        assertEquals(RequestResult.SUCCESSFUL, out.requestResult());
        verify(customerTrackingClient, times(1)).notifyLogin(customerId);

        verify(resultRepository, times(1)).insertIgnore(
                any(UUID.class),
                eq(messageId),
                eq(customerId),
                eq("Samira"),
                eq(Client.ANDROID.name()),
                eq(ts),
                eq("10.0.0.2"),
                eq(RequestResult.SUCCESSFUL.name())
        );
    }


    @Test
    void processWhenNewMessage_andRestFailsThenSucceeds_shouldRetryAndReturnSuccessful() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "Samira", "ios", ts, messageId, "10.0.0.3"
        );

        LoginTrackingResultEntity saved = toEntity(in, RequestResult.SUCCESSFUL);
        saved.setId(UUID.randomUUID());

        when(resultRepository.findByMessageId(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(saved));

        when(customerTrackingClient.notifyLogin(customerId))
                .thenThrow(new RestClientException("boom1"))
                .thenThrow(new RestClientException("boom2"))
                .thenReturn(true);

        when(resultRepository.insertIgnore(any(), eq(messageId), eq(customerId),
                eq("Samira"), eq(Client.IOS.name()), eq(ts), eq("10.0.0.3"), eq(RequestResult.SUCCESSFUL.name())))
                .thenReturn(1);

        when(payloadSerializer.serialize(any())).thenReturn("{}".getBytes());
        when(outboxRepository.insertIgnore(any(), anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);

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
                customerId, "davod", "web", ts, messageId, "10.0.0.4"
        );

        LoginTrackingResultEntity saved = toEntity(in, RequestResult.UNSUCCESSFUL);
        saved.setId(UUID.randomUUID());

        when(resultRepository.findByMessageId(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(saved));

        when(customerTrackingClient.notifyLogin(customerId))
                .thenThrow(new RestClientException("fail1"))
                .thenThrow(new RestClientException("fail2"))
                .thenThrow(new RestClientException("fail3"));

        when(resultRepository.insertIgnore(any(), eq(messageId), eq(customerId),
                eq("davod"), eq(Client.WEB.name()), eq(ts), eq("10.0.0.4"), eq(RequestResult.UNSUCCESSFUL.name())))
                .thenReturn(1);

        when(payloadSerializer.serialize(any())).thenReturn("{}".getBytes());
        when(outboxRepository.insertIgnore(any(), anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);

        var out = service.process(in);

        assertEquals(RequestResult.UNSUCCESSFUL, out.requestResult());
        verify(customerTrackingClient, times(3)).notifyLogin(customerId);
    }


    @Test
    void process_whenInsertIgnoreConflicts_shouldLoadExistingAndReturnIt() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant ts = Instant.now();

        CustomerLoginEvent in = new CustomerLoginEvent(customerId, "u", "web", ts, messageId, "10.0.0.1");

        LoginTrackingResultEntity existing = toEntity(in, RequestResult.SUCCESSFUL);
        existing.setId(UUID.randomUUID());

        when(resultRepository.findByMessageId(messageId))
                .thenReturn(Optional.empty(), Optional.of(existing));

        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);

        when(resultRepository.insertIgnore(
                any(UUID.class),
                eq(messageId),
                eq(customerId),
                eq("u"),
                eq(Client.WEB.name()),
                eq(ts),
                eq("10.0.0.1"),
                eq(RequestResult.SUCCESSFUL.name())
        )).thenReturn(0);

        byte[] payload = "{}".getBytes();
        when(payloadSerializer.serialize(any())).thenReturn(payload);

        when(outboxRepository.insertIgnore(
                any(UUID.class),
                eq(AggregateType.LOGIN_TRACKING_RESULT.name()),
                eq(existing.getId()),
                eq(IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED.name()),
                eq("login-tracking-result"),
                eq(customerId.toString()),
                eq(payload)
        )).thenReturn(0);

        var out = service.process(in);

        assertEquals(messageId, out.messageId());
        assertEquals(RequestResult.SUCCESSFUL, out.requestResult());

        verify(resultRepository, never()).save(any());
        verify(resultRepository, times(1)).insertIgnore(
                any(UUID.class),
                eq(messageId),
                eq(customerId),
                eq("u"),
                eq(Client.WEB.name()),
                eq(ts),
                eq("10.0.0.1"),
                eq(RequestResult.SUCCESSFUL.name())
        );
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

        UUID savedId = UUID.randomUUID();
        LoginTrackingResultEntity saved = toEntity(in, RequestResult.SUCCESSFUL);
        saved.setId(savedId);

        when(resultRepository.findByMessageId(messageId))
                .thenReturn(Optional.empty(), Optional.of(saved));

        when(customerTrackingClient.notifyLogin(customerId)).thenReturn(true);

        when(resultRepository.insertIgnore(
                any(UUID.class),
                eq(messageId),
                eq(customerId),
                eq("bob"),
                eq(Client.ANDROID.name()),
                eq(ts),
                eq("10.0.0.2"),
                eq(RequestResult.SUCCESSFUL.name())
        )).thenReturn(1);

        byte[] payload = "{}".getBytes();
        when(payloadSerializer.serialize(any())).thenReturn(payload);

        when(outboxRepository.insertIgnore(
                any(UUID.class),
                eq(AggregateType.LOGIN_TRACKING_RESULT.name()),
                eq(savedId),
                eq(IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED.name()),
                eq("login-tracking-result"),
                eq(customerId.toString()),
                eq(payload)
        )).thenReturn(0);

        assertDoesNotThrow(() -> {
            var out = service.process(in);
            assertEquals(RequestResult.SUCCESSFUL, out.requestResult());
            assertEquals(messageId, out.messageId());
        });

        verify(resultRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());

        verify(resultRepository, times(1)).insertIgnore(
                any(UUID.class),
                eq(messageId),
                eq(customerId),
                eq("bob"),
                eq(Client.ANDROID.name()),
                eq(ts),
                eq("10.0.0.2"),
                eq(RequestResult.SUCCESSFUL.name())
        );

        verify(outboxRepository, times(1)).insertIgnore(
                any(UUID.class),
                eq(AggregateType.LOGIN_TRACKING_RESULT.name()),
                eq(savedId),
                eq(IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED.name()),
                eq("login-tracking-result"),
                eq(customerId.toString()),
                eq(payload)
        );
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

        UUID savedId = UUID.randomUUID();
        LoginTrackingResultEntity saved = toEntity(in, RequestResult.UNSUCCESSFUL);
        saved.setId(savedId);

        when(resultRepository.findByMessageId(messageId))
                .thenReturn(Optional.empty(), Optional.of(saved));

        when(customerTrackingClient.notifyLogin(customerId))
                .thenThrow(new RestClientException("fail1"))
                .thenThrow(new RestClientException("fail2"))
                .thenThrow(new RestClientException("fail3"));

        when(resultRepository.insertIgnore(
                any(UUID.class),
                eq(messageId),
                eq(customerId),
                eq("carol"),
                eq(Client.IOS.name()),
                eq(ts),
                eq("10.0.0.3"),
                eq(RequestResult.UNSUCCESSFUL.name())
        )).thenReturn(1);

        byte[] payload = "{}".getBytes();
        when(payloadSerializer.serialize(any())).thenReturn(payload);

        when(outboxRepository.insertIgnore(
                any(UUID.class),
                eq(AggregateType.LOGIN_TRACKING_RESULT.name()),
                eq(savedId),
                eq(IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED.name()),
                eq("login-tracking-result"),
                eq(customerId.toString()),
                eq(payload)
        )).thenReturn(1);

        var out = service.process(in);

        assertEquals(RequestResult.UNSUCCESSFUL, out.requestResult());
        verify(customerTrackingClient, times(3)).notifyLogin(customerId);

        verify(resultRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());

        verify(resultRepository, times(1)).insertIgnore(
                any(UUID.class),
                eq(messageId),
                eq(customerId),
                eq("carol"),
                eq(Client.IOS.name()),
                eq(ts),
                eq("10.0.0.3"),
                eq(RequestResult.UNSUCCESSFUL.name())
        );

        verify(outboxRepository, times(1)).insertIgnore(
                any(UUID.class),
                eq(AggregateType.LOGIN_TRACKING_RESULT.name()),
                eq(savedId),
                eq(IntegrationEventType.LOGIN_TRACKING_RESULT_CREATED.name()),
                eq("login-tracking-result"),
                eq(customerId.toString()),
                eq(payload)
        );

        verify(resultRepository, times(2)).findByMessageId(messageId);
    }


}