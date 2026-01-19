package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;

public interface OutboxPayloadSerializer {
    byte[] serialize(LoginTrackingResultEvent event);
}
