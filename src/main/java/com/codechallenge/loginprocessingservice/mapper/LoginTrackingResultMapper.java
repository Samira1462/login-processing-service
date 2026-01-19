package com.codechallenge.loginprocessingservice.mapper;

import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;
import com.codechallenge.loginprocessingservice.persistence.model.LoginTrackingResultEntity;

public final class LoginTrackingResultMapper {

    private LoginTrackingResultMapper() {
    }

    public static LoginTrackingResultEvent toEvent(LoginTrackingResultEntity e) {
        return new LoginTrackingResultEvent(
                e.getCustomerId(),
                e.getUsername(),
                e.getClient().name().toLowerCase(),
                e.getEventTimestamp(),
                e.getMessageId(),
                e.getCustomerIp(),
                e.getRequestResult()
        );
    }
}
