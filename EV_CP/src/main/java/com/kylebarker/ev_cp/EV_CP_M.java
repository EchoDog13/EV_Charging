package com.kylebarker.ev_cp;

import com.fasterxml.jackson.databind.JsonNode;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class EV_CP_M implements Runnable {

    @Option(names = { "-cp", "--central-port" }, description = "Central server port", required = true)
    Integer centralPort;

    @Option(names = { "-ci", "--central-ip" }, description = "Central server IP or hostname", required = true)
    String centralIP;

    @Option(names = { "-ep", "--engine-port" }, description = "Port of the engine")
    Integer enginePort;

    @Option(names = { "-ei", "--engine-ip" }, description = "Engine IP or hostname")
    String engineIP;

    @Option(names = { "-u", "--uid" }, description = "Unique Identifier of charge point")
    int uid;

    @Option(names = { "-c", "--cost" }, description = "Price per KW (default: ${DEFAULT-VALUE})")
    double pricePerKW = 10;

    @Option(names = { "-r", "--register" }, description = "Register with the central server")
    boolean registerFlag = false;

    @Option(names = { "-l", "--location" }, description = "Charging point location")
    String cpLocation;

    Socket centralSocket = null;
    BufferedReader in = null;
    PrintWriter out = null;

    String state = "DISCONNECTED";

    private Thread healthCheckThread;
    private volatile boolean running = true;

    @Override
    public void run() {
        // Print parsed configuration
        //

        // Initialize a socket connection with the central server
        initConnection();

        if (registerFlag) {
            // Check that all the required arguments are given for registration
            if ((centralIP != null) && (!centralIP.isEmpty()) && (centralPort > 0) && enginePort > 0 && engineIP != null
                    && !engineIP.isEmpty()) {
                register();
            }
        }

        // Listen for incoming messages from server
        try {
            String serverMsg;
            while ((serverMsg = in.readLine()) != null) {
                System.out.println("Received from server: " + serverMsg);
                handleServerMessage(serverMsg);
            }
        } catch (IOException e) {
            System.out.println("Connection closed: " + e.getMessage());
        } finally {
            stopHealthCheck();
            try {
                if (centralSocket != null)
                    centralSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initConnection() {
        int retries = 10;
        int waitTime = 1000;
        for (int i = 0; i < retries; i++) {
            try {
                centralSocket = new Socket(centralIP, centralPort);
                in = new BufferedReader(new InputStreamReader(centralSocket.getInputStream()));
                out = new PrintWriter(centralSocket.getOutputStream(), true);
                System.out.println("Connected to central server!");
                break;
            } catch (IOException e) {
                System.err.println("Connection attempt " + (i + 1) + " failed: " + e.getMessage());
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    public void register() {
        System.out.println("Registering with central server at " + centralIP + ":" + centralPort);

        // Establish connection with central server
        if (centralSocket == null) {
            initConnection();
        }

        // Create JSON register request
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();

        json.put("function", "register");
        json.put("uid", uid);
        json.put("pricePerKW", pricePerKW);
        json.put("location", cpLocation);
        json.put("state", state);

        // Send request
        out.println(json.toString());

        // Await response
        try {
            String response = in.readLine();
            System.out.println("Registration response: " + response);

            JsonNode node = mapper.readTree(response);

            // FIXED: Check for status field (not function) and success value
            if (node.has("status") && node.has("function")) {
                String status = node.get("status").asText();
                String function = node.get("function").asText();

                if (status.equals("success") && function.equals("register")) {
                    System.out.println("Registration successful! Starting health checks...");
                    state = "ACTIVATED";
                    startHealthCheck();
                }

                else if (status.equals("error") && function.equals("register")) {
                    System.exit(1);
                }
            }

        } catch (IOException e) {
            System.err.println("Error during registration: " + e.getMessage());
        }
    }

    private void handleServerMessage(String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(message);

            if (node.has("function")) {
                String function = node.get("function").asText();
                switch (function) {
                    case "setCPState":
                        setStatus(node);
                        break;
                    // Add other function handlers as needed
                }
            }
        } catch (IOException e) {
            System.err.println("Error parsing server message: " + e.getMessage());
        }
    }

    /**
     * Sets the status of the CP
     */
    private void setStatus(JsonNode node) {
        if (node.has("state")) {
            state = node.get("state").asText();
            System.out.println("Status updated to: " + state);
        }
    }

    private void startHealthCheck() {
        healthCheckThread = new Thread(() -> {
            while (running) {
                try {
                    sendHealthCheck();
                    Thread.sleep(1000); // Wait 1 second
                } catch (InterruptedException e) {
                    System.out.println("Health check thread interrupted");
                    break;
                } catch (Exception e) {
                    System.err.println("Error in health check: " + e.getMessage());
                }
            }
        });
        healthCheckThread.start();
        System.out.println("Health checks started");
    }

    private void sendHealthCheck() {
        if (out != null && centralSocket != null && !centralSocket.isClosed()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode healthCheckJson = mapper.createObjectNode();
                healthCheckJson.put("function", "healthcheck");
                healthCheckJson.put("uid", uid);
                healthCheckJson.put("timestamp", System.currentTimeMillis());
                healthCheckJson.put("state", state);

                out.println(healthCheckJson.toString());
                System.out.println("Health check sent - UID: " + uid + ", State: " + state);
            } catch (Exception e) {
                System.err.println("Failed to send health check: " + e.getMessage());
                stopHealthCheck();
            }
        }
    }

    private void stopHealthCheck() {
        running = false;
        if (healthCheckThread != null) {
            healthCheckThread.interrupt();
        }
        System.out.println("Health checks stopped");
    }

    public static void main(String[] args) {
        EV_CP_M client = new EV_CP_M();

        // Add shutdown hook for proper cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            client.stopHealthCheck();
        }));

        int exitCode = new CommandLine(client).execute(args);
        System.exit(exitCode);
    }
}