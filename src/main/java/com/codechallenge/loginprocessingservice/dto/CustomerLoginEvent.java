package com.codechallenge.loginprocessingservice.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record CustomerLoginEvent(
        @NotNull
        UUID customerId,

        @NotBlank
        @Size(max = 100)
        String username,

        @NotBlank
        @Pattern(regexp = "(?i)web|android|ios", message = "client must be web/android/ios")
        String client,

        @NotNull
        @PastOrPresent
        Instant timestamp,

        @NotNull
        UUID messageId,

        @NotBlank
        @Size(max = 45)
        String customerIp
) {}
