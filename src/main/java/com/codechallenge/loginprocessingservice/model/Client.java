package com.codechallenge.loginprocessingservice.model;

public enum Client {
    WEB,
    ANDROID,
    IOS;

    public static Client fromString(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("client is null");
        }

        raw = raw.toUpperCase();

        return switch (raw) {
            case "WEB" -> WEB;
            case "ANDROID" -> ANDROID;
            case "IOS" -> IOS;
            default -> {
                throw new IllegalArgumentException("Unsupported client: " + raw);
            }
        };
    }
}