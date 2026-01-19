package com.codechallenge.loginprocessingservice.dto;

import com.codechallenge.loginprocessingservice.persistence.model.RequestResult;

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
) {
    public static LoginTrackingResultEvent from(CustomerLoginEvent in, RequestResult result) {
        return new LoginTrackingResultEvent(
                in.customerId(),
                in.username(),
                in.client(),
                in.timestamp(),
                in.messageId(),
                in.customerIp(),
                result
        );
    }
}