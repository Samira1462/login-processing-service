package com.codechallenge.loginprocessingservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(ContainersConfig.class)
public abstract class AbstractTest {

    @LocalServerPort
    public int port;

    protected static WireMockContainer wireMockContainer = new WireMockContainer("wiremock/wiremock:latest")
            .withExposedPorts(8080);

    static {
        wireMockContainer.start();
    }

    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        registry.add("wiremock.base-url", () -> wireMockContainer.getBaseUrl());
    }
}