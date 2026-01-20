package com.codechallenge.loginprocessingservice.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerLoginEvent(
        UUID customerId,
        String username,
        String client,
        Instant timestamp,
        UUID messageId,
        String customerIp
) {}
