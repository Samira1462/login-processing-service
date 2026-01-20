package com.codechallenge.loginprocessingservice.service;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;

public interface LoginProcessingService {
    LoginTrackingResultEvent process(CustomerLoginEvent event);
}
