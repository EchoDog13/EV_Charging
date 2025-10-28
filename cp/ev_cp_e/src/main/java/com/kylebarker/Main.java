package com.kylebarker;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(exclude = { org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class })
@EnableKafka
@EnableDiscoveryClient

public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}