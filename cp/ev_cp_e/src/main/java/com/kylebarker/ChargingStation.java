package com.kylebarker;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.Socket;
import org.json.JSONObject;

@Component
public class ChargingStation implements CommandLineRunner {

    public static String chargerId;
    public static PrintWriter writer;
    public static BufferedReader reader;
    public static String status = "OK";

    @Override
    public void run(String... args) throws Exception {
        // This runs after Spring Boot starts
        connectMonitor();
    }

    private void connectMonitor() {
        String hostname = "host.docker.internal";
        int port = 5050;

        try (Socket socket = new Socket(hostname, port);
                BufferedReader localReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter localWriter = new PrintWriter(socket.getOutputStream(), true)) {

            reader = localReader;
            writer = localWriter;
            startHealthCheck();
            System.out.println("Connected to EV_CP_M at " + hostname + ":" + port);

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject json = new JSONObject(line);
                    String command = json.getString("command");

                    switch (command) {
                        case "CONNECT_MONITOR":
                            startHealthCheck();
                            break;
                        case "STOP_CHARGING":
                            chargerId = json.getString("chargerId");
                            stopCharging(chargerId);
                            break;
                        case "STATUS_UPDATE":
                            handleStatusUpdate(json);
                            break;
                        default:
                            System.out.println("Unknown command: " + command);
                    }
                } catch (Exception e) {
                    System.err.println("Invalid JSON received: " + line);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to connect to EV_CP_M: " + e.getMessage());
        }
    }

    private void startHealthCheck() {
        System.out.println("Starting health check...");
        Thread healthCheckThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000);
                    sendHealthCheck(status);
                } catch (InterruptedException e) {
                    System.err.println("Health check thread interrupted: " + e.getMessage());
                    break;
                }
            }
        });
        healthCheckThread.start();
    }

    private void startCharging(String chargerId) {
        System.out.println("Starting charging on: " + chargerId);
    }

    private void stopCharging(String chargerId) {
        System.out.println("Stopping charging on: " + chargerId);
    }

    private void handleStatusUpdate(JSONObject json) {
        System.out.println("Status update: " + json.toString());
    }

    private void sendHealthCheck(String status) {
        try {
            JSONObject json = new JSONObject();
            json.put("function", "healthcheck");
            json.put("chargerId", chargerId);
            json.put("state", status);
            json.put("timestamp", System.currentTimeMillis());

            writer.println(json.toString());
            System.out.println("Health check sent: " + json.toString());
        } catch (Exception e) {
            System.err.println("Failed to send health check: " + e.getMessage());
        }
    }
}