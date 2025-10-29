package com.kylebarker.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylebarker.ChargingSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KafkaReceiver {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ChargingSession> activeSessions = new ConcurrentHashMap<>();

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
            String chargerId = json.has("chargerId") ? json.get("chargerId").asText(null) : null;
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
        if (activeSessions.values().stream()
                .anyMatch(s -> s.getChargerId().equals(localChargerId)
                        && !s.getStatus().equals("COMPLETED"))) {
            System.out.println("⚠️ Charger " + localChargerId + " already in use. Rejecting new session.");
            return;
        }

        ChargingSession session = new ChargingSession(localChargerId, driverId);
        activeSessions.put(session.getSessionId(), session);
        System.out.println("🟢 New charging session created: " + session.getSessionId());
    }

    private void handleStopCharging() {
        activeSessions.values().forEach(session -> {
            if (session.getChargerId().equals(localChargerId)
                    && !session.getStatus().equals("COMPLETED")) {
                session.end();
            }
        });
    }

    private void handlePlugIn() {
        activeSessions.values().forEach(session -> {
            if (session.getChargerId().equals(localChargerId)
                    && session.getStatus().equals("WAITING_FOR_PLUG")) {
                session.plugIn();
            }
        });
    }

    private void handleUnplug() {
        activeSessions.values().forEach(session -> {
            if (session.getChargerId().equals(localChargerId)
                    && !session.getStatus().equals("COMPLETED")) {
                session.unplug();
            }
        });
    }

    private void handlePause() {
        activeSessions.values().forEach(session -> {
            if (session.getChargerId().equals(localChargerId)
                    && session.getStatus().equals("IN_PROGRESS")) {
                session.pause();
            }
        });
    }

    private void handleResume() {
        activeSessions.values().forEach(session -> {
            if (session.getChargerId().equals(localChargerId)
                    && session.getStatus().equals("PAUSED")) {
                session.resume();
            }
        });
    }

    // === GLOBAL CONTROLS ===

    private void pauseAllSessions() {
        System.out.println("⏸ Pausing all sessions...");
        activeSessions.values().forEach(ChargingSession::pause);
    }

    private void resumeAllSessions() {
        System.out.println("▶️ Resuming all sessions...");
        activeSessions.values().forEach(ChargingSession::resume);
    }

    private void handleStopAll() {
        System.out.println(
                "⏹ Stop all received: stopping all non-completed sessions (paused sessions will not be resumed)...");

        // End any session that isn't already completed. Do NOT resume paused sessions
        // first —
        // stopAll should immediately request sessions to end, regardless of their
        // current
        // paused state. Resuming should only happen on startAll.
        activeSessions.values().forEach(session -> {
            try {
                if (!"COMPLETED".equals(session.getStatus())) {
                    session.end();
                }
            } catch (Exception e) {
                System.err.println("Failed to stop session " + session.getSessionId() + ": " + e.getMessage());
            }
        });

        // Remove completed sessions from the map
        try {
            activeSessions.entrySet().removeIf(entry -> "COMPLETED".equals(entry.getValue().getStatus()));
        } catch (Exception e) {
            // Fallback: iterate keys and remove
            activeSessions.keySet().removeIf(key -> {
                ChargingSession s = activeSessions.get(key);
                return s != null && "COMPLETED".equals(s.getStatus());
            });
        }
    }

    private void printStatus() {
        activeSessions.values().forEach(s -> System.out.println("ℹ️ " + s.getSessionInfo()));
    }

    public String apiStartSession(String chargerId, String driverId) {
        if (activeSessions.values().stream().anyMatch(s -> s.getChargerId().equals(chargerId)
                && !"COMPLETED".equals(s.getStatus()))) {
            return "❌ Charger " + chargerId + " already in use.";
        }
        ChargingSession session = new ChargingSession(chargerId, driverId);
        activeSessions.put(session.getSessionId(), session);
        return "✅ Session started for charger " + chargerId + " (waiting for plug)";
    }

    public String apiStopSession(String chargerId) {
        ChargingSession session = getSessionByCharger(chargerId);
        if (session == null)
            return "⚠️ No active session for " + chargerId;
        session.end();
        activeSessions.remove(session.getSessionId());
        return "🛑 Session stopped for " + chargerId + ". Energy: " +
                String.format("%.3f", session.getEnergyConsumed()) + " kWh, cost $" +
                String.format("%.2f", session.getTotalCost());
    }

    public String apiPlugIn(String chargerId) {
        ChargingSession session = getSessionByCharger(chargerId);
        if (session == null)
            return "⚠️ No session found for " + chargerId;
        session.plugIn();
        return "🔌 Charger " + chargerId + " plugged in.";
    }

    public String apiUnplug(String chargerId) {
        ChargingSession session = getSessionByCharger(chargerId);
        if (session == null)
            return "⚠️ No session found for " + chargerId;
        session.unplug();
        return "🔌 Charger " + chargerId + " unplugged.";
    }

    public String apiPause(String chargerId) {
        ChargingSession session = getSessionByCharger(chargerId);
        if (session == null)
            return "⚠️ No session found for " + chargerId;
        session.pause();
        return "⏸️ Charger " + chargerId + " paused.";
    }

    public String apiResume(String chargerId) {
        ChargingSession session = getSessionByCharger(chargerId);
        if (session == null)
            return "⚠️ No session found for " + chargerId;
        session.resume();
        return "▶️ Charger " + chargerId + " resumed.";
    }

    public String apiStatus(String chargerId) {
        ChargingSession session = getSessionByCharger(chargerId);
        if (session == null)
            return "ℹ️ Charger " + chargerId + " idle.";
        return String.format(
                "⚡ Charger %s — Status: %s | Energy: %.3f kWh | Cost: $%.2f | Connected: %b",
                chargerId, session.getStatus(), session.getEnergyConsumed(),
                session.getTotalCost(), session.isChargerConnected());
    }

    // --- Helper ---
    private ChargingSession getSessionByCharger(String chargerId) {
        return activeSessions.values().stream()
                .filter(s -> s.getChargerId().equals(chargerId))
                .findFirst()
                .orElse(null);
    }
}