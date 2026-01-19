package com.codechallenge.loginprocessingservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    private final Logger logger = LoggerFactory.getLogger(RestClientConfig.class.getName());

    @Bean
    @Qualifier("customerTrackingRestClient")
    public RestClient customerTrackingRestClient(
            @Value("${app.customer-tracking.base-url}") String baseUrl,
            @Value("${app.customer-tracking.username}") String username,
            @Value("${app.customer-tracking.password}") String password) {

        logger.info("Configuring CustomerTracking RestClient with baseUrl={}", baseUrl);

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(h -> h.setBasicAuth(username, password))
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }
}
