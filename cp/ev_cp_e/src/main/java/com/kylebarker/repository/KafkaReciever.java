
package com.kylebarker.repository;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaReciever {
    @KafkaListener(topics = "cp", groupId = "ev_central_group")
    public void listen(String message) {
        System.out.println("Received message: " + message);
        // process your message here}

        //

    }
}
