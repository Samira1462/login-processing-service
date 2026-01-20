package com.codechallenge.loginprocessingservice.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.wiremock.integrations.testcontainers.WireMockContainer;
import static org.junit.jupiter.api.Assertions.*;

import com.codechallenge.loginprocessingservice.config.RestClientConfig;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import java.util.UUID;

class CustomerTrackingClientTest {
    private static final String TRACK_LOGIN_PATH = "/v1/api/trackLoging/";

    private static final WireMockContainer wireMock =
            new WireMockContainer("wiremock/wiremock:3.6.0");

    private CustomerTrackingClient client;

    private final String username = "tracking_user";
    private final String password = "tracking_password";

    @BeforeAll
    static void startWiremock() {
        wireMock.start();
    }

    @AfterAll
    static void stopWiremock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {

        RestClientConfig cfg = new RestClientConfig();

        RestClient restClient = cfg.customerTrackingRestClient(
                wireMock.getBaseUrl(),
                username,
                password
        );

        client = new CustomerTrackingClient(restClient);

        WireMock.configureFor(wireMock.getHost(), wireMock.getPort());
        reset();
    }

    @Test
    void notifyLogin_whenRequestSucceeds_shouldReturnTrueAndSendBasicAuth() {
        UUID customerId = UUID.randomUUID();

        stubFor(
                get(urlEqualTo(TRACK_LOGIN_PATH + customerId))
                        .willReturn(aResponse().withStatus(200))
        );

        boolean ok = client.notifyLogin(customerId);

        assertTrue(ok);

        verify(
                1,
                getRequestedFor(urlEqualTo(TRACK_LOGIN_PATH + customerId))
                        .withBasicAuth(new BasicCredentials(username, password))
        );
    }

    @Test
    void notifyLogin_whenServerError_shouldThrowRestClientResponseException() {
        UUID customerId = UUID.randomUUID();

        stubFor(
                get(urlEqualTo(TRACK_LOGIN_PATH + customerId))
                        .willReturn(aResponse().withStatus(500))
        );

        assertThrows(Exception.class, () -> client.notifyLogin(customerId));

        verify(1, getRequestedFor(urlEqualTo(TRACK_LOGIN_PATH + customerId)));
    }

    @Test
    void notifyLogin_whenClientError_shouldThrowRestClientResponseException() {
        UUID customerId = UUID.randomUUID();

        stubFor(
                get(urlEqualTo(TRACK_LOGIN_PATH + customerId))
                        .willReturn(aResponse().withStatus(401))
        );

        assertThrows(RestClientResponseException.class, () -> client.notifyLogin(customerId));

        verify(
                1,
                getRequestedFor(urlEqualTo(TRACK_LOGIN_PATH + customerId))
                        .withBasicAuth(new BasicCredentials(username, password))
        );
    }

    @Test
    void notifyLogin_whenConnectionDropped_shouldThrowResourceAccessException() {
        UUID customerId = UUID.randomUUID();

        stubFor(
                get(urlEqualTo(TRACK_LOGIN_PATH + customerId))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        );

        assertThrows(ResourceAccessException.class, () -> client.notifyLogin(customerId));

        verify(1, getRequestedFor(urlEqualTo(TRACK_LOGIN_PATH + customerId)));
    }

}
