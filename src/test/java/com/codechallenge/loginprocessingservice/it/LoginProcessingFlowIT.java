package com.codechallenge.loginprocessingservice.it;

import com.codechallenge.loginprocessingservice.AbstractTest;
import com.codechallenge.loginprocessingservice.config.KafkaTestProducerConfig;
import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.model.PublicationStatus;
import com.codechallenge.loginprocessingservice.model.RequestResult;
import com.codechallenge.loginprocessingservice.repository.LoginTrackingResultRepository;
import com.codechallenge.loginprocessingservice.repository.OutboxRepository;
import com.codechallenge.loginprocessingservice.service.LoginProcessingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@Import(KafkaTestProducerConfig.class)
public class LoginProcessingFlowIT extends AbstractTest {

    @Autowired
    private KafkaTemplate<String, CustomerLoginEvent> customerLoginKafkaTemplate;

    @Autowired
    private LoginTrackingResultRepository resultRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private LoginProcessingServiceImpl processingService;

    @BeforeEach
    void setUp() {
        configureFor(wireMockContainer.getHost(), wireMockContainer.getFirstMappedPort());
        outboxRepository.deleteAll();
        resultRepository.deleteAll();
    }

    @Test
    void shouldProcessEventOnlyOnce_whenSameMessageIdIsReceived() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId)).willReturn(aResponse().withStatus(204)));

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId,
                "Samira",
                "web",
                Instant.now(),
                messageId,
                "10.0.0.1"
        );

        customerLoginKafkaTemplate.send("customer-login", customerId.toString(), in);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var opt = resultRepository.findByMessageId(messageId);
            assertTrue(opt.isPresent());

            var saved = opt.get();
            assertEquals(customerId, saved.getCustomerId());
            assertEquals(RequestResult.SUCCESSFUL, saved.getRequestResult());

            var outboxRows = outboxRepository.findAll();
            long matches = outboxRows.stream()
                    .filter(o -> o.getStatus() == PublicationStatus.NEW)
                    .filter(o -> "login-tracking-result".equals(o.getTopic()))
                    .filter(o -> customerId.toString().equals(o.getKey()))
                    .count();

            assertEquals(1L, matches);
        });

        verify(1, getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
    }

    @Test
    void shouldProcessEventOnlyOnce_whenSameMessageIdIsReceivedConcurrently() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                .willReturn(aResponse().withStatus(204)));

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId,
                "Samira",
                "web",
                Instant.now(),
                messageId,
                "10.0.0.1"
        );

        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        Runnable sendTask = () -> {
            try {
                start.await();
                customerLoginKafkaTemplate.send("customer-login", customerId.toString(), in);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };

        pool.submit(sendTask);
        pool.submit(sendTask);


        start.countDown();

        assertTrue(done.await(3, TimeUnit.SECONDS));

        pool.shutdown();

        await().atMost(15, SECONDS).untilAsserted(() -> {
            var opt = resultRepository.findByMessageId(messageId);
            assertTrue(opt.isPresent());

            var saved = opt.get();
            assertEquals(customerId, saved.getCustomerId());
            assertEquals(RequestResult.SUCCESSFUL, saved.getRequestResult());

            var outboxRows = outboxRepository.findAll();
            long matches = outboxRows.stream()
                    .filter(o -> o.getStatus() == PublicationStatus.NEW)
                    .filter(o -> "login-tracking-result".equals(o.getTopic()))
                    .filter(o -> customerId.toString().equals(o.getKey()))
                    .count();

            assertEquals(1L, matches);
        });

        verify(1, getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
    }

    @Test
    void shouldPersistSingleResultAndSingleOutbox_whenSameMessageIdIsReceivedConcurrently() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                .willReturn(aResponse().withStatus(204)));

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId,
                "Samira",
                "web",
                Instant.now(),
                messageId,
                "10.0.0.1"
        );

        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        Runnable sendTask = () -> {
            try {
                start.await();
                customerLoginKafkaTemplate.send("customer-login", customerId.toString(), in);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };

        pool.submit(sendTask);
        pool.submit(sendTask);

        start.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        pool.shutdown();

        await().atMost(15, SECONDS).untilAsserted(() -> {

            var allResults = resultRepository.findAll();
            long resultCount = allResults.stream()
                    .filter(r -> messageId.equals(r.getMessageId()))
                    .count();
            assertEquals(1L, resultCount);

            var saved = allResults.stream()
                    .filter(r -> messageId.equals(r.getMessageId()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(customerId, saved.getCustomerId());
            assertEquals(RequestResult.SUCCESSFUL, saved.getRequestResult());

            var outboxRows = outboxRepository.findAll();
            long outboxMatches = outboxRows.stream()
                    .filter(o -> o.getStatus() == PublicationStatus.NEW)
                    .filter(o -> "login-tracking-result".equals(o.getTopic()))
                    .filter(o -> customerId.toString().equals(o.getKey()))
                    .count();

            assertEquals(1L, outboxMatches);
        });

        verify(moreThanOrExactly(1), getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
        verify(lessThanOrExactly(2), getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
    }

    @Test
    void shouldNotRetry_on4xx_andPersistUnsuccessful() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                .willReturn(aResponse().withStatus(400)));

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "Samira", "web", Instant.now(), messageId, "10.0.0.1"
        );

        customerLoginKafkaTemplate.send("customer-login", customerId.toString(), in);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            var saved = resultRepository.findByMessageId(messageId).orElseThrow();
            assertEquals(RequestResult.UNSUCCESSFUL, saved.getRequestResult());

            assertEquals(1L, outboxRepository.count());
        });

        verify(1, getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
    }

    @Test
    void shouldBeIdempotent_atDatabaseLevel_whenProcessIsCalledConcurrently() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                .willReturn(aResponse().withStatus(204).withFixedDelay(200)));

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "Samira", "web", Instant.now(), messageId, "10.0.0.1"
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> f1 = pool.submit(() -> processingService.process(in));
        Future<?> f2 = pool.submit(() -> processingService.process(in));

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        await().atMost(10, SECONDS).untilAsserted(() -> {
            long results = resultRepository.findAll().stream()
                    .filter(r -> messageId.equals(r.getMessageId()))
                    .count();
            assertEquals(1L, results);

            long outboxNew = outboxRepository.findAll().stream()
                    .filter(o -> o.getStatus() == PublicationStatus.NEW)
                    .count();
            assertEquals(1L, outboxNew);
        });

        verify(moreThanOrExactly(1), getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
        verify(lessThanOrExactly(2), getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
    }

    @Test
    void shouldRetry_on5xx_andEventuallySucceed() {
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("second")
                .willReturn(aResponse().withStatus(500)));

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                .inScenario("retry")
                .whenScenarioStateIs("second")
                .willSetStateTo("third")
                .willReturn(aResponse().withStatus(500)));

        stubFor(get(urlEqualTo("/v1/api/trackLoging/" + customerId))
                .inScenario("retry")
                .whenScenarioStateIs("third")
                .willReturn(aResponse().withStatus(204)));

        CustomerLoginEvent in = new CustomerLoginEvent(
                customerId, "Samira", "web", Instant.now(), messageId, "10.0.0.1"
        );

        customerLoginKafkaTemplate.send("customer-login", customerId.toString(), in);

        await().atMost(15, SECONDS)
                .until(() -> resultRepository.findByMessageId(messageId).isPresent());

        var saved = resultRepository.findByMessageId(messageId).orElseThrow();
        assertEquals(RequestResult.SUCCESSFUL, saved.getRequestResult());


        verify(3, getRequestedFor(urlEqualTo("/v1/api/trackLoging/" + customerId)));
    }
}