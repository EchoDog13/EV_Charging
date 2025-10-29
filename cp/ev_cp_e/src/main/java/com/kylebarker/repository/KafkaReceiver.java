package com.kylebarker.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylebarker.ChargingSession;
import com.kylebarker.ChargingStation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaReceiver {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ChargingStation station;

    public KafkaReceiver(KafkaSender kafkaSender) {
        this.kafkaSender = kafkaSender;
    }

    private final KafkaSender kafkaSender;

    // Make the local charger ID configurable via application.properties or
    // environment variable.
    // Make the local charger ID required via application.properties or environment
    // variable.
    // Do NOT provide a default here so Spring will fail to start if the property is
    // missing.
    @Value("${charger.id}")
    private String localChargerId;

    @KafkaListener(topics = "CP", groupId = "ev_central_group")
    public void listen(String message) {
        System.out.println("Received message: " + message);
        try {
            JsonNode json = objectMapper.readTree(message);
            // Some producers (or accidental string-wrapping in Kafka messages) send
            // the JSON as a quoted string, e.g. "{\"type\":\"stopAll\"}".
            // In that case the root node is textual and we need to parse the inner
            // content to obtain the actual object fields.
            if (json.isTextual()) {
                System.out.println("Received textual JSON wrapper; unwrapping inner JSON...");
                json = objectMapper.readTree(json.asText());
            }
            String type = json.path("type").asText("default");
            // Do NOT default missing chargerId to the local charger. If absent, treat as
            // null and
            // ignore (for non-global messages). This prevents accidental creation of
            // sessions on
            // the local charger when the incoming message omitted the field.
            // Accept either "chargerId" or the driver/central field name "cpUid" so
            // external producers that use cpUid still work.
            String chargerId = null;
            if (json.has("chargerId")) {
                chargerId = json.get("chargerId").asText(null);
            } else if (json.has("cpUid")) {
                chargerId = json.get("cpUid").asText(null);
            }
            String driverId = json.path("driverId").asText("unknown");

            // global pause/resume messages have no charger ID, so skip check
            if (!type.equals("stopAll") && !type.equals("startAll")) {
                if (chargerId == null) {
                    System.out.println("Ignoring message without chargerId for type '" + type + "'");
                    return;
                }
                if (!chargerId.equals(localChargerId)) {
                    System.out
                            .println("Ignoring message for charger " + chargerId + " (this is " + localChargerId + ")");
                    return;
                }
            }

            switch (type) {
                case "startCharging" -> handleStartCharging(driverId);
                case "stopCharging" -> handleStopCharging();
                case "plugIn" -> handlePlugIn();
                case "unplug" -> handleUnplug();
                case "pause" -> handlePause();
                case "resume" -> handleResume();
                case "stopAll" -> handleStopAll();
                case "pauseAll" -> pauseAllSessions();
                case "resumeAll" -> resumeAllSessions();
                case "status" -> printStatus();
                default -> System.out.println("Unknown message type: " + type);
            }

        } catch (Exception e) {
            System.err.println("Failed to process message: " + e.getMessage());
        }
    }

    // === INDIVIDUAL CHARGER CONTROLS ===

    private void handleStartCharging(String driverId) {

        String messageString;
        // Delegate session creation to the shared ChargingStation so the REST
        // status endpoints reflect the same sessions.
        String startResult = station.startSession(localChargerId, driverId);
        // startResult contains the session id if created; attempt to plug in
        // immediately so it transitions to IN_PROGRESS (supplying).
        try {
            station.plugIn(localChargerId);
        } catch (Exception e) {
            System.err.println("Failed to auto-plug session via ChargingStation: " + e.getMessage());
        }
        messageString = "🟢 New charging session created and supplying (via station): " + startResult;
        kafkaSender.send("charge_requests", messageString);
    }

    private void handleStopCharging() {
        station.stopSession(localChargerId);
    }

    private void handlePlugIn() {
        station.plugIn(localChargerId);
    }

    private void handleUnplug() {
        station.unplug(localChargerId);
    }

    private void handlePause() {
        station.pause(localChargerId);
    }

    private void handleResume() {
        station.resume(localChargerId);
    }

    // === GLOBAL CONTROLS ===

    private void pauseAllSessions() {
        System.out.println("⏸ Pausing all sessions...");
        station.pauseAll();
    }

    private void resumeAllSessions() {
        System.out.println("▶️ Resuming all sessions...");
        station.resumeAll();
    }

    private void handleStopAll() {
        System.out.println(
                "⏹ Stop all received: stopping all non-completed sessions (paused sessions will not be resumed)...");
        station.stopAll();
    }

    private void printStatus() {
        ChargingSession s = station.getActiveSession(localChargerId);
        if (s != null)
            System.out.println("ℹ️ " + s.getSessionInfo());
    }

    public String apiStartSession(String chargerId, String driverId) {
        // Delegate to ChargingStation which enforces one session per charger
        return station.startSession(chargerId, driverId);
    }

    public String apiStopSession(String chargerId) {
        return station.stopSession(chargerId);
    }

    public String apiPlugIn(String chargerId) {
        return station.plugIn(chargerId);
    }

    public String apiUnplug(String chargerId) {
        return station.unplug(chargerId);
    }

    public String apiPause(String chargerId) {
        return station.pause(chargerId);
    }

    public String apiResume(String chargerId) {
        return station.resume(chargerId);
    }

    public String apiStatus(String chargerId) {
        ChargingSession session = station.getActiveSession(chargerId);
        if (session == null)
            return "ℹ️ Charger " + chargerId + " idle.";
        return String.format(
                "⚡ Charger %s — Status: %s | Energy: %.3f kWh | Cost: $%.2f | Connected: %b",
                chargerId, session.getStatus(), session.getEnergyConsumed(),
                session.getTotalCost(), session.isChargerConnected());
    }

    // --- end ---
}