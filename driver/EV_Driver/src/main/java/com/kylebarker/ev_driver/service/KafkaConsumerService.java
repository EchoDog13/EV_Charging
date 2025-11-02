package com.kylebarker.ev_driver.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private final DriverService driverService;

    public KafkaConsumerService(DriverService driverService) {
        this.driverService = driverService;
    }

    @KafkaListener(topics = { "charge_requests", "charge_responses", "CP", "broadcast" }, groupId = "ev_group")
    public void listen(String message) {
        System.out.println("Received message: " + message);
        driverService.addMessage(message);
    }
}
