package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonOutboxPayloadSerializer implements OutboxPayloadSerializer{

    private final ObjectMapper objectMapper;
    public JsonOutboxPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(LoginTrackingResultEvent event) {
        try {
            return objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize LoginTrackingResultEvent", e);
        }
    }
}
