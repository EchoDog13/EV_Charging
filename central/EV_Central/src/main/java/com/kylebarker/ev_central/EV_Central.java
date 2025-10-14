package com.kylebarker.ev_central;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kylebarker.ev_central.model.Charger;
import com.kylebarker.ev_central.model.chargerState;
import com.kylebarker.ev_central.repository.ChargerRepository;
import com.kylebarker.ev_central.repository.KafkaSender;

import jakarta.persistence.criteria.CriteriaBuilder.In;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;

@SpringBootApplication
public class EV_Central {

    public static void main(String[] args) {
        // Parse CLI args with picocli
        CommandLineArgs cmd = new CommandLineArgs();
        new CommandLine(cmd).parseArgs(args);

        // Start Spring Boot
        ApplicationContext context = SpringApplication.run(EV_Central.class, args);

        // Start the central server
        CentralServer server = context.getBean(CentralServer.class);
        server.start(cmd.port);
    }
}

// Simple POJO for command line arguments
class CommandLineArgs {
    @Option(names = { "-p", "--port" }, description = "Port for the server to listen on", defaultValue = "5500")
    public int port;

    @Option(names = { "-ss", "--setstate" }, description = "Set the state of a charging point")
    public chargerState chargerState;
}

/**
 * Socket server that handles charger connections
 */
@Component
class CentralServer {

    private final ChargerRepository chargerRepository;
    private final ConcurrentHashMap<Long, Socket> chargerSockets = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final KafkaSender kafkaSender;

    public CentralServer(ChargerRepository chargerRepository, KafkaSender kafkaSender) {
        this.chargerRepository = chargerRepository;
        this.kafkaSender = kafkaSender;
    }

    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))) {
            System.out.println("Central server listening on port " + port);

            kafkaSender.send("central_startup", "Central server started on port " + port);

            // --- Background thread to monitor health checks ---
            new Thread(() -> {
                while (true) {
                    try {
                        long now = System.currentTimeMillis();

                        // Iterate over all chargers in the database
                        for (Charger charger : chargerRepository.findAll()) {
                            Long lastSeen = charger.getLastHealthCheck();
                            if (lastSeen == null || now - lastSeen > 5000) { // 5 second timeout
                                if (charger.getState() != chargerState.DISCONNECTED) {
                                    charger.setState(chargerState.DISCONNECTED);
                                    chargerRepository.save(charger);
                                    System.out.println("Charger " + charger.getUid()
                                            + " marked as DISCONNECTED due to missing health check.");
                                }
                            }
                        }

                        Thread.sleep(1000); // check every 1 second
                    } catch (InterruptedException e) {
                        break; // exit thread if interrupted
                    }
                }
            }).start();

            // --- Main server loop for handling clients ---
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // Handle each client in a separate thread
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to start server on port " + port, e);
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received: " + line); // Debug logging

                String function = null;
                try {
                    JsonNode node = mapper.readTree(line);

                    if (node.has("function")) {
                        function = node.get("function").asText();
                    }

                    if (function.equals("register")) {
                        Charger charger = registerChargerDB(node);
                        chargerSockets.put(charger.getUid(), clientSocket);

                        // Send proper JSON response
                        sendSuccessResponse(out, "register", "Charger registered successfully", charger);
                    }

                    else if (function.equals("healthcheck")) {
                        chargerState state = chargerState.valueOf(node.get("state").asText());
                        Long uid = Long.parseLong(node.get("uid").asText());

                        // Charger charger = chargerRepository.findById(uid);
                        Charger charger = chargerRepository.findById(uid).orElseThrow();
                        charger.setState(state);
                        charger.setLastHealthCheck(System.currentTimeMillis());
                        chargerRepository.save(charger);

                    } else {
                        // If no function field, send error response
                        sendErrorResponse(out, "Missing 'function' field in request", function);
                    }

                } catch (JsonProcessingException e) {
                    // Handle JSON parsing errors
                    sendErrorResponse(out, "Invalid JSON format: " + e.getMessage(), function);
                } catch (Exception e) {
                    // Handle other exceptions
                    sendErrorResponse(out, "Server error: " + e.getMessage(), function);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client disconnected.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Charger registerChargerDB(JsonNode node) {
        try {
            Charger charger = mapper.treeToValue(node, Charger.class);

            // Fixed the logic - check if UID is NOT null and already exists
            if ((charger.getUid() != null) && chargerRepository.findById(charger.getUid()).isPresent()) {
                throw new RuntimeException("Charger with id " + charger.getUid() + " already exists.");
            }

            Charger savedCharger = chargerRepository.save(charger);
            System.out.println("Charger registered in database: " + savedCharger.getUid());
            return savedCharger;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to register charger", e);
        }
    }

    /**
     * Send a success response with charger data
     */
    private void sendSuccessResponse(PrintWriter out, String function, String message, Charger charger) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("function", function);
            response.put("status", "success");
            response.put("message", message);
            response.put("timestamp", System.currentTimeMillis());

            // Convert charger object to JSON and add to response
            ObjectNode chargerJson = mapper.valueToTree(charger);
            response.set("charger", chargerJson);

            String jsonResponse = response.toString();
            System.out.println("Sending success response: " + jsonResponse);
            out.println(jsonResponse);

        } catch (Exception e) {
            System.err.println("Error creating success response: " + e.getMessage());
            sendErrorResponse(out, "Failed to create response", function);
        }
    }

    /**
     * Send an error response
     */
    private void sendErrorResponse(PrintWriter out, String errorMessage, String function) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("function", function);
            response.put("status", "error");
            response.put("message", errorMessage);
            response.put("timestamp", System.currentTimeMillis());

            String jsonResponse = response.toString();
            System.out.println("Sending error response: " + jsonResponse);
            out.println(jsonResponse);

        } catch (Exception e) {
            System.err.println("Error creating error response: " + e.getMessage());
            // Fallback plain text response
            out.println("{\"status\":\"error\",\"message\":\"Critical server error\"}");
        }
    }

    public void setCPState(long chargerUID, chargerState state) {
        try {
            Charger charger = chargerRepository.findById(chargerUID)
                    .orElseThrow(() -> new RuntimeException("Charger not found: " + chargerUID));

            ObjectNode jsonNode = mapper.createObjectNode();
            jsonNode.put("function", "setCPState");
            jsonNode.put("charger", charger.getUid());
            jsonNode.put("state", state.toString());

            Socket chargerSocket = chargerSockets.get(charger.getUid());
            if (chargerSocket != null && !chargerSocket.isClosed()) {
                PrintWriter out = new PrintWriter(chargerSocket.getOutputStream(), true);
                out.println(jsonNode.toString());
                System.out.println("Sent setCPState command to charger " + charger.getUid());
            } else {
                System.err.println("Charger socket not found or closed for UID: " + charger.getUid());
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to set CP state for charger " + chargerUID, e);
        }
    }

}