package com.kylebarker.ev_driver.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    @KafkaListener(topics = "charge_requests", groupId = "ev_group")
    public void listen(String message) {
        System.out.println("Received message: " + message);
    }
}
