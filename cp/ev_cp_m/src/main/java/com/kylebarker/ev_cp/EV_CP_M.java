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

    Socket centralSocket = null;
    BufferedReader in = null;
    PrintWriter out = null;

    String state = "DISCONNECTED";

    private Thread healthCheckThread;
    private volatile boolean running = true;

    private ExecutorService engineThreadPool = Executors.newCachedThreadPool();
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void run() {
        // Print parsed configuration
        //

        // Initialize a socket connection with the central server
        initConnection();
        startEngineListener();

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
                System.err.println("centralIP: " + centralIP + "centralport: " + centralPort);
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
        if (centralSocket == null || centralSocket.isClosed()) {
            initConnection();
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();

        json.put("function", "register");
        json.put("uid", uid);
        json.put("pricePerKW", pricePerKW);
        json.put("location", cpLocation);
        json.put("state", state);

        // Send registration request
        String registrationJson = json.toString();
        System.out.println("Sending registration: " + registrationJson);
        out.println(registrationJson);
        out.flush();

        boolean registered = false;
        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds

        try {
            // Set a read timeout on the socket
            centralSocket.setSoTimeout(10000);

            while (!registered && (System.currentTimeMillis() - startTime < timeout)) {
                String response = in.readLine(); // This will block for up to 10 seconds

                if (response == null) {
                    System.err.println("Connection closed by server during registration");
                    break;
                }

                System.out.println("Registration response: " + response);

                try {
                    JsonNode node = mapper.readTree(response);

                    if (node.has("status") && node.has("function")) {
                        String status = node.get("status").asText();
                        String function = node.get("function").asText();

                        if ("success".equals(status) && "register".equals(function)) {
                            System.out.println("Registration successful! Starting health checks...");
                            state = "ACTIVATED";
                            registered = true;
                            startHealthCheck();
                            break; // Success, exit loop
                        } else if ("error".equals(status) && "register".equals(function)) {
                            System.err.println("Registration failed. Server returned error.");
                            break;
                        } else {
                            // Not a registration response, continue waiting
                            System.out
                                    .println("Received unrelated message, still waiting for registration response...");
                            continue;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing registration response: " + e.getMessage());
                    // Continue waiting for valid response
                }
            }

            if (!registered) {
                System.err.println("Registration timed out or failed after " + timeout + "ms");
            }

        } catch (java.net.SocketTimeoutException e) {
            System.err.println("Registration timeout: No response from server within 10 seconds");
        } catch (IOException e) {
            System.err.println("Error during registration: " + e.getMessage());
        } finally {
            try {
                centralSocket.setSoTimeout(0); // Reset timeout
            } catch (IOException e) {
                // Ignore
            }
        }

        if (!registered) {
            System.err.println("Shutting down due to registration failure...");
            stopHealthCheck();
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

                        // Send acknowledgment back
                        ObjectNode ack = mapper.createObjectNode();
                        ack.put("function", "ack");
                        ack.put("status", "received");
                        writer.println(ack.toString());
                    } else {
                        System.out.println("Unknown message from engine: " + line);
                    }
                } catch (Exception e) {
                    System.err.println("Invalid JSON from engine: " + line);
                }
            }

        } catch (IOException e) {
            System.err.println("Engine connection closed: " + e.getMessage());
        }
    }
}