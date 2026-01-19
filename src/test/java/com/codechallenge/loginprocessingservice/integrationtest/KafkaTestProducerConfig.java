package com.codechallenge.loginprocessingservice.integrationtest;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@TestConfiguration
public class KafkaTestProducerConfig {

    @Bean
    public ProducerFactory<String, CustomerLoginEvent> customerLoginProducerFactory(
            org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties
    ) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, CustomerLoginEvent> customerLoginKafkaTemplate(
            ProducerFactory<String, CustomerLoginEvent> customerLoginProducerFactory
    ) {
        return new KafkaTemplate<>(customerLoginProducerFactory);
    }
}
