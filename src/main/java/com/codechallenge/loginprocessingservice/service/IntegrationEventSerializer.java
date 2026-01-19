package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;

public interface IntegrationEventSerializer {
    byte[] serialize(LoginTrackingResultEvent event);
}
