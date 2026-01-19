package com.codechallenge.loginprocessingservice.kafka.config;

import com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CustomerLoginEvent> customerLoginKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties
    ) {
        var props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.codechallenge.loginprocessingservice");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent");

        ConsumerFactory<String, CustomerLoginEvent> cf =
                new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                        props,
                        new StringDeserializer(),
                        new JsonDeserializer<>(CustomerLoginEvent.class, false)
                );

        var factory = new ConcurrentKafkaListenerContainerFactory<String, CustomerLoginEvent>();
        factory.setConsumerFactory(cf);


        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}