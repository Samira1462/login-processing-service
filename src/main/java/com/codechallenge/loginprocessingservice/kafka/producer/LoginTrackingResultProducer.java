package com.codechallenge.loginprocessingservice.kafka.producer;


import com.codechallenge.loginprocessingservice.dto.LoginTrackingResultEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoginTrackingResultProducer {
    private final KafkaTemplate<String, LoginTrackingResultEvent> kafkaTemplate;

    public LoginTrackingResultProducer(
            KafkaTemplate<String, LoginTrackingResultEvent> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(LoginTrackingResultEvent event) {
        kafkaTemplate.send(event.customerId().toString(), event);
    }
}
