package com.codechallenge.loginprocessingservice.mapper;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;
import com.codechallenge.loginprocessingservice.model.LoginTrackingResultEntity;
import com.codechallenge.loginprocessingservice.model.RequestResult;

import static com.codechallenge.loginprocessingservice.mapper.ClientMapper.toClient;

public final class LoginTrackingResultMapper {

    private LoginTrackingResultMapper() {
    }

    public static LoginTrackingResultEntity toEntity(CustomerLoginEvent event, RequestResult requestResult) {
        LoginTrackingResultEntity e = new LoginTrackingResultEntity();
        e.setMessageId(event.messageId());
        e.setCustomerId(event.customerId());
        e.setUsername(event.username());
        e.setClient(toClient(event.client()));
        e.setEventTimestamp(event.timestamp());
        e.setCustomerIp(event.customerIp());
        e.setRequestResult(requestResult);
        return e;
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
