package com.codechallenge.loginprocessingservice.mapper;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;
import com.codechallenge.loginprocessingservice.model.Client;
import com.codechallenge.loginprocessingservice.model.LoginTrackingResultEntity;
import com.codechallenge.loginprocessingservice.model.RequestResult;

public final class LoginTrackingResultMapper {

    private LoginTrackingResultMapper() {
    }

    public static LoginTrackingResultEntity toEntity(CustomerLoginEvent event, RequestResult requestResult) {
        LoginTrackingResultEntity entity = new LoginTrackingResultEntity();
        entity.setMessageId(event.messageId());
        entity.setCustomerId(event.customerId());
        entity.setUsername(event.username());
        entity.setClient(Client.fromString(event.client()));
        entity.setEventTimestamp(event.timestamp());
        entity.setCustomerIp(event.customerIp());
        entity.setRequestResult(requestResult);
        return entity;
    }

    public static LoginTrackingResultEvent toEvent(LoginTrackingResultEntity event) {
        return new LoginTrackingResultEvent(
                event.getCustomerId(),
                event.getUsername(),
                event.getClient().name().toLowerCase(),
                event.getEventTimestamp(),
                event.getMessageId(),
                event.getCustomerIp(),
                event.getRequestResult()
        );
    }
}
