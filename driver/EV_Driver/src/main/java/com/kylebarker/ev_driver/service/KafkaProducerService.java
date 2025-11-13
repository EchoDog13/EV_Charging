package com.kylebarker.ev_driver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, Object message) {
        try {
            Object payload = message;
            // If the configured producer expects StringSerializer for values,
            // ensure we send a String. Otherwise send as-is.
            if (!(message instanceof String)) {
                payload = objectMapper.writeValueAsString(message);
            }
            kafkaTemplate.send(topic, payload);
            System.out.println("Sent message to topic " + topic + ": " + payload);
        } catch (Exception e) {
            System.err.println("Failed to send message to Kafka: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
