package com.kylebarker.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kylebarker.ChargingStation;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple socket client that periodically sends a healthcheck JSON to the
 * monitor service. This is a plain Java client (not a Spring component) so it
 * can be instantiated directly by application code.
 */
public class EngineClient {

    private final ChargingStation station;
    private final ObjectMapper mapper = new ObjectMapper();

    // defaults; can be overridden by env vars
    // Use the monitor host IP by default in this environment
    private String masterHost;
    private int masterPort = Integer.parseInt(System.getenv("MONITOR_HOST_PORT")); // Need to change this

    private volatile Socket socket;
    private volatile PrintWriter writer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "engine-client");
        t.setDaemon(true);
        return t;
    });

    public EngineClient(ChargingStation station) {
        this.station = station;
        // resolve from environment if available
        String envHost = System.getenv("ENGINE_HOST");
        String envPort = System.getenv("ENGINE_PORT");
        if (envHost != null && !envHost.isBlank())
            masterHost = envHost.trim();
        if (envPort != null && !envPort.isBlank()) {
            try {
                masterPort = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        startClient();

        // ensure we clean up on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                scheduler.shutdownNow();
            } catch (Exception ignored) {
            }
            closeConnection();
        }));
    }

    private void startClient() {
        System.out.println("EngineClient resolved target -> host: " + masterHost + ", port: " + masterPort);
        // schedule ensureConnectionAndSend every 5 seconds
        scheduler.scheduleWithFixedDelay(this::ensureConnectionAndSend, 0, 5, TimeUnit.SECONDS);
    }

    private void ensureConnectionAndSend() {
        try {
            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                tryConnect();
            }

            if (writer != null) {
                sendHealthCheck();
            }
        } catch (Exception e) {
            System.err.println("EngineClient error: " + e.getMessage());
            closeConnection();
        }
    }

    private void tryConnect() {
        closeConnection();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(masterHost, masterPort), 5000);
            writer = new PrintWriter(socket.getOutputStream(), true);
            station.addMessage("EngineClient connected to master " + masterHost + ":" + masterPort);
            System.out.println("EngineClient connected to " + masterHost + ":" + masterPort);
        } catch (IOException e) {
            System.err.println(
                    "EngineClient failed to connect to " + masterHost + ":" + masterPort + " - " + e.getMessage());
            closeConnection();
        }
    }

    private void sendHealthCheck() {
        try {
            String chargerId = station.getChargerId();
            String state = station.getState(chargerId);

            ObjectNode json = mapper.createObjectNode();
            json.put("function", "healthcheck");
            try {
                json.put("uid", Integer.parseInt(chargerId));
            } catch (NumberFormatException ex) {
                json.put("uid", -1);
            }
            json.put("timestamp", Instant.now().toEpochMilli());
            json.put("state", state);

            String payload = json.toString();
            writer.println(payload);
            writer.flush();
            station.addMessage("OUT Engine->monitor: " + payload);
        } catch (Exception e) {
            System.err.println("Failed to send healthcheck: " + e.getMessage());
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (writer != null)
                writer.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null)
                socket.close();
        } catch (Exception ignored) {
        }
        writer = null;
        socket = null;
    }

    /**
     * Close resources and stop the background scheduler. Can be called by
     * the owner to cleanly stop the client.
     */
    public void shutdown() {
        scheduler.shutdownNow();
        closeConnection();
    }
}
