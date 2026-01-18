package com.codechallenge.loginprocessingservice.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class CustomerTrackingClient {

    private static final String TRACK_LOGIN_PATH = "/v1/api/trackLoging/{customerId}";

    private final Logger logger = LoggerFactory.getLogger(CustomerTrackingClient.class);

    private final RestClient restClient;

    public CustomerTrackingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean notifyLogin(UUID customerId) {
        logger.debug("Sending login tracking request for customerId={}", customerId);

        restClient.get()
                .uri(TRACK_LOGIN_PATH, customerId)
                .retrieve()
                .toBodilessEntity();

        logger.debug("Login tracking request successful for customerId={}", customerId);
        return true;
    }
}
