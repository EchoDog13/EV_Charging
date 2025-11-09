package com.kylebarker.ev_driver.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private final DriverService driverService;

    public KafkaConsumerService(DriverService driverService) {
        this.driverService = driverService;
    }

    // Read consumer group id from Spring property so different container instances
    // can be assigned distinct group ids via environment variables
    @KafkaListener(topics = { "charge_requests", "charge_responses", "CP",
            "broadcast" }, groupId = "${spring.kafka.consumer.group-id:ev_group}")
    public void listen(String message) {
        System.out.println("Received message: " + message);
        driverService.addMessage(message);
    }
}
