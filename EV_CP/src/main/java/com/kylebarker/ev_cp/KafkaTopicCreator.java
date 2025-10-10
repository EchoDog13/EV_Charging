package com.kylebarker.ev_cp;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.Collections;
import java.util.Properties;

public class KafkaTopicCreator {

    public static void createTopicsIfNotExist(String bootstrapServers) {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(config)) {

            // Define topics
            NewTopic cpCommand = new NewTopic("cp.command", 1, (short) 1);
            NewTopic chargeRequest = new NewTopic("charge.request", 1, (short) 1);
            NewTopic chargeAuth = new NewTopic("charge.auth", 1, (short) 1);
            NewTopic chargeSession = new NewTopic("charge.session", 1, (short) 1);
            NewTopic billingTicket = new NewTopic("billing.ticket", 1, (short) 1);

            // Create topics
            admin.createTopics(
                    java.util.List.of(cpCommand, chargeRequest, chargeAuth, chargeSession, billingTicket)).all().get(); // blocks
                                                                                                                        // until
                                                                                                                        // topics
                                                                                                                        // are
                                                                                                                        // created

            System.out.println("Topics created successfully!");

        } catch (Exception e) {
            System.err.println("Failed to create topics: " + e.getMessage());
        }
    }

    // public static void main(String[] args) {
    // createTopicsIfNotExist("kafka:9092");
    // }
}