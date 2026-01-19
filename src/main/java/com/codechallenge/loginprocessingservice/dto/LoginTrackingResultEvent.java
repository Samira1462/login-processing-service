package com.codechallenge.loginprocessingservice.dto;

import com.codechallenge.loginprocessingservice.model.RequestResult;

import java.time.Instant;
import java.util.UUID;

public record LoginTrackingResultEvent(
        UUID customerId,
        String username,
        String client,
        Instant timestamp,
        UUID messageId,
        String customerIp,
        RequestResult requestResult
) {}