package com.kylebarker;

import java.io.*;
import java.net.Socket;
import org.json.JSONObject;

public class Main {
    public static void main(String[] args) {
        // Open socket connection to EV_CP_M
        connectMonitor();
    }

    public static String chargerId;
    public static PrintWriter writer;
    public static BufferedReader reader;
    public static String status = "OK";

    private static void connectMonitor() {
        String hostname = "host.docker.internal";
        int port = 5050;

        try (Socket socket = new Socket(hostname, port);
                BufferedReader localReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter localWriter = new PrintWriter(socket.getOutputStream(), true)) {

            // Assign to class-level variables
            reader = localReader;
            writer = localWriter;

            System.out.println("Connected to EV_CP_M at " + hostname + ":" + port);

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject json = new JSONObject(line);

                    // Expecting JSON like: {"command":"START_CHARGING","chargerId":"CHG_001"}
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

    private static void startHealthCheck() {
        // TODO Auto-generated method stub
        System.out.println("Starting health check...");

        Thread healthCheckThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000); // 10 seconds
                    sendHealthCheck(status);
                } catch (InterruptedException e) {
                    System.err.println("Health check thread interrupted: " + e.getMessage());
                    break;
                }
            }
        });
        healthCheckThread.start();
    }

    private static void sendHealthCheck(String status) {
        JSONObject json = new JSONObject();
        json.put("chargerId", chargerId);
        json.put("status", status);

        writer.println(json.toString());
        System.out.println("Health check sent.");
    }

    private static void startCharging(String chargerId) {
        System.out.println("Starting charging on: " + chargerId);
        // TODO: Implement start charging process
    }

    private static void stopCharging(String chargerId) {
        System.out.println("Stopping charging on: " + chargerId);
        // TODO: Implement stop charging process
    }

    private static void handleStatusUpdate(JSONObject json) {
        System.out.println("Status update: " + json.toString());
        // TODO: Implement status update handling
    }
}