package com.codechallenge.loginprocessingservice.mapper;

import com.codechallenge.loginprocessingservice.model.Client;

public final class ClientMapper {

    private ClientMapper() {}

    public static Client toClient(String raw) {
        if (raw == null) {
            // TODO: decide invalid behavior (DLQ / mark unsuccessful / reject)
            throw new IllegalArgumentException("client is null");
        }

        String normalized = raw.trim().toUpperCase();

        return switch (normalized) {
            case "WEB" -> Client.WEB;
            case "ANDROID" -> Client.ANDROID;
            case "IOS" -> Client.IOS;
            default -> {
                // TODO: decide invalid behavior (DLQ / mark unsuccessful / reject)
                throw new IllegalArgumentException("Unsupported client: " + raw);
            }
        };
    }
}
