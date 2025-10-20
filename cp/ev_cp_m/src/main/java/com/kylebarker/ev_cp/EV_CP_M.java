package com.kylebarker.ev_cp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private volatile Socket centralSocket = null;
    private volatile BufferedReader in = null;
    private volatile PrintWriter out = null;

    private volatile String state = "DISCONNECTED";
    private volatile boolean running = true;

    private Thread healthCheckThread;
    private Thread centralReaderThread;
    private ExecutorService engineThreadPool = Executors.newCachedThreadPool();
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void run() {
        startEngineListener();

        if (registerFlag) {
            attemptRegistration();
        }

        startHealthCheck();

        // Start central message reader in a separate thread
        startCentralReader();

        // Keep main thread alive
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void startCentralReader() {
        centralReaderThread = new Thread(() -> {
            while (running) {
                if (in != null) {
                    try {
                        String serverMsg = in.readLine();
                        if (serverMsg == null) {
                            System.err.println("Central closed connection. Attempting reconnect...");
                            reconnectCentral();
                            continue;
                        }
                        handleServerMessage(serverMsg);
                    } catch (IOException e) {
                        System.err.println("Error reading from central: " + e.getMessage());
                        reconnectCentral();
                    }
                } else {
                    reconnectCentral();
                }
            }
        });
        centralReaderThread.setDaemon(true);
        centralReaderThread.start();
    }

    private void reconnectCentral() {
        closeCentral();

        while (running) {
            try {
                System.out.println("Attempting to reconnect to central...");
                centralSocket = new Socket(centralIP, centralPort);
                in = new BufferedReader(new InputStreamReader(centralSocket.getInputStream()));
                out = new PrintWriter(centralSocket.getOutputStream(), true);
                System.out.println("Reconnected to central!");

                if (registerFlag) {
                    attemptRegistration();
                }

                // Set state to ACTIVATED on reconnect
                state = "ACTIVATED";
                System.out.println("State set to ACTIVATED after reconnect.");

                break;
            } catch (IOException e) {
                System.err.println("Reconnect failed, retrying in 5s...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void closeCentral() {
        try {
            if (centralSocket != null)
                centralSocket.close();
        } catch (IOException ignored) {
        }
        centralSocket = null;
        in = null;
        out = null;
    }

    private void attemptRegistration() {
        if (centralSocket == null || centralSocket.isClosed()) {
            reconnectCentral();
        }

        try {
            ObjectNode json = mapper.createObjectNode();
            json.put("function", "register");
            json.put("uid", uid);
            json.put("pricePerKW", pricePerKW);
            json.put("location", cpLocation);
            json.put("state", state);

            out.println(json.toString());
            out.flush();
            System.out.println("Registration sent: " + json.toString());
        } catch (Exception e) {
            System.err.println("Registration failed: " + e.getMessage());
        }
    }

    private void startHealthCheck() {
        healthCheckThread = new Thread(() -> {
            while (running) {
                sendHealthCheck();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        });
        healthCheckThread.setDaemon(true);
        healthCheckThread.start();
        System.out.println("Health checks started");
    }

    private void sendHealthCheck() {
        if (out != null) {
            try {
                ObjectNode healthCheckJson = mapper.createObjectNode();
                healthCheckJson.put("function", "healthcheck");
                healthCheckJson.put("uid", uid);
                healthCheckJson.put("timestamp", System.currentTimeMillis());
                healthCheckJson.put("state", state);
                out.println(healthCheckJson.toString());

                // Print to monitor log
                System.out.println("Health check sent: " + healthCheckJson.toString());
            } catch (Exception e) {
                System.err.println("Failed to send health check: " + e.getMessage());
            }
        } else {
            System.out.println("Health check not sent: central not connected.");
        }
    }

    private void handleServerMessage(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            if (node.has("function")) {
                String function = node.get("function").asText();
                switch (function) {
                    case "setCPState":
                        setStatus(node);
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("Invalid JSON from server: " + e.getMessage());
        }
    }

    private void setStatus(JsonNode node) {
        if (node.has("state")) {
            state = node.get("state").asText();
            System.out.println("Status updated to: " + state);
        }
    }

    private void startEngineListener() {
        Thread listenerThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(enginePort, 0, InetAddress.getByName("0.0.0.0"))) {
                System.out.println(
                        "EV_CP_M listening for engine connections on port " + enginePort + " on all interfaces");

                while (running) {
                    Socket engineSocket = serverSocket.accept();
                    System.out.println("Engine connected: " + engineSocket.getInetAddress());
                    engineThreadPool.submit(() -> handleEngineConnection(engineSocket));
                }
            } catch (IOException e) {
                if (running)
                    System.err.println("Engine listener error: " + e.getMessage());
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void handleEngineConnection(Socket engineSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(engineSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(engineSocket.getOutputStream(), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode json = mapper.readTree(line);
                    if (json.has("function") && "healthcheck".equals(json.get("function").asText())) {
                        int uid = json.has("uid") ? json.get("uid").asInt() : -1;
                        String state = json.has("state") ? json.get("state").asText() : "UNKNOWN";
                        long timestamp = json.has("timestamp") ? json.get("timestamp").asLong()
                                : System.currentTimeMillis();

                        System.out.println("Health check received - UID: " + uid + ", State: " + state + ", Timestamp: "
                                + timestamp);

                        ObjectNode ack = mapper.createObjectNode();
                        ack.put("function", "ack");
                        ack.put("status", "received");
                        writer.println(ack.toString());
                    }
                } catch (Exception e) {
                    System.err.println("Invalid JSON from engine: " + line);
                }
            }

        } catch (IOException e) {
            System.err.println("Engine connection closed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        EV_CP_M client = new EV_CP_M();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            client.running = false;
        }));

        int exitCode = new CommandLine(client).execute(args);
        System.exit(exitCode);
    }
}