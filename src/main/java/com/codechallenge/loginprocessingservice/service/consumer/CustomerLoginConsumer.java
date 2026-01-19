package com.codechallenge.loginprocessingservice.service.consumer;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import com.codechallenge.loginprocessingservice.service.LoginProcessingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
public class CustomerLoginConsumer {

    private static final Logger log = LoggerFactory.getLogger(CustomerLoginConsumer.class);

    private final LoginProcessingService processingService;

    public CustomerLoginConsumer(LoginProcessingService processingService) {
        this.processingService = processingService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.input}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "customerLoginKafkaListenerContainerFactory"
    )
    public void onMessage(@Valid CustomerLoginEvent event, Acknowledgment ack) {
        log.info("[DEBUG_LOG] Received customer-login event messageId={} customerId={}", event.messageId(), event.customerId());

        processingService.process(event);

        ack.acknowledge();
    }
}