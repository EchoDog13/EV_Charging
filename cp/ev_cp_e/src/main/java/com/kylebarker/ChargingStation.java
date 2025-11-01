package com.kylebarker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.kylebarker.repository.KafkaSender;
import com.kylebarker.repository.EngineClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.List;

@Component
public class ChargingStation {

    private final Map<String, ChargingSession> activeSessionsByCharger = new ConcurrentHashMap<>();
    private final Map<String, ChargingSession> activeSessionsById = new ConcurrentHashMap<>();
    private final Timer energyUpdater = new Timer(true);
    // In-memory recent message buffer (Kafka messages / diagnostics) for UI
    private final Deque<String> recentMessages = new ConcurrentLinkedDeque<>();

    private final String chargerId;
    private final KafkaSender kafkaSender;
    private final EngineClient engineClient;
    private String globalState;

    // Charger ID is now required to be supplied via configuration; no
    // auto-generation.

    public String getChargerId() {
        return chargerId;
    }

    // Expose current active session count for UI/status endpoints
    public int getActiveSessionCount() {
        return activeSessionsByCharger.size();
    }

    // Return a simple state string for the given charger id.
    // Maps internal session statuses to friendly states expected by the UI / API.
    public String getState(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null) {
            // No active session — treat as available/activated

            if (globalState == null) {
                return "activated";
            } else {
                return globalState;
            }
            String status = session.getStatus();
            switch (status) {
                case "IN_PROGRESS":
                    return "supplying";
                case "HOLD":
                    return "waiting";
                case "PAUSED":
                    return "paused";
                case "COMPLETED":
                    return "completed";
                default:
                    return status.toLowerCase();
            }
        }
    }

    public ChargingStation(@Value("${charger.id:}") String configuredChargerId, KafkaSender kafkaSender) {
        if (configuredChargerId == null || configuredChargerId.isBlank()) {
            throw new IllegalStateException(
                    "Required property 'charger.id' is missing or blank. Set via -Dcharger.id=... or CHARGER_ID env.");
        }
        this.chargerId = configuredChargerId;
        this.kafkaSender = kafkaSender;

        // Start the plain socket EngineClient (not managed by Spring)
        EngineClient client = new EngineClient(this);
        this.engineClient = client;

        // ensure we shutdown the engine client on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                client.shutdown();
            } catch (Exception ignored) {
            }
        }));

        System.out.println("Charging Station started with ID: " + chargerId + " (configured)");

        energyUpdater.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Update energy for all sessions and publish telemetry for
                // sessions that are actively supplying.
                for (ChargingSession s : activeSessionsByCharger.values()) {
                    try {
                        s.updateEnergy();
                        if ("IN_PROGRESS".equals(s.getStatus()) && s.isChargerConnected()) {
                            // build a telemetry payload
                            java.util.Map<String, Object> payload = new java.util.HashMap<>();
                            payload.put("type", "telemetry");
                            payload.put("cpUid", chargerId);
                            payload.put("sessionId", s.getSessionId());
                            payload.put("driverId", s.getDriverId());
                            payload.put("energy_kWh", s.getEnergyConsumed());
                            payload.put("power_kW", s.getPowerKw());
                            payload.put("cost_eur", s.getTotalCost());
                            payload.put("timestamp", java.time.Instant.now().toString());
                            // send to telemetry topic
                            try {
                                kafkaSender.send("cp_telemetry", payload);
                            } catch (Exception ex) {
                                System.err.println("Failed to send telemetry: " + ex.getMessage());
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Error updating session energy/telemetry: " + ex.getMessage());
                    }
                }
            }
        }, 0, 1000);
    }

    // Add a textual message to the local buffer (newest first). Keeps up to
    // 100 messages.
    public void addMessage(String message) {
        if (message == null)
            return;
        recentMessages.addFirst(message);
        while (recentMessages.size() > 100) {
            recentMessages.removeLast();
        }
    }

    // Return the recent messages as a list (newest first)
    public List<String> getRecentMessages() {
        return new ArrayList<>(recentMessages);
    }

    // Update local state of the charging point
    public String updateState(String chargerId, String state) {
        // Example: just log for now
        globalState = state;
        return "Updated charger " + chargerId + " state to " + state;
    }

    // Start session by chargerId
    public String startSession(String chargerId, String driverId) {
        ChargingSession existing = activeSessionsByCharger.get(chargerId);
        if (existing != null) {
            if (existing.getStatus().equals("PAUSED")) {
                existing.resume();
                return "Resumed paused session on charger " + chargerId;
            }
            return "Charger " + chargerId + " is already in use!";
        }

        ChargingSession session = new ChargingSession(chargerId, driverId);
        activeSessionsByCharger.put(chargerId, session);
        activeSessionsById.put(session.getSessionId(), session);
        return "Created new session (waiting for plug): " + session.getSessionId();
    }

    // Start session by sessionId
    public String startSessionById(String sessionId) {
        ChargingSession session = activeSessionsById.get(sessionId);
        if (session == null)
            return "No session found with ID " + sessionId;

        if (session.getStatus().equals("PAUSED")) {
            session.resume();
            return "Resumed paused session " + sessionId;
        }
        return "Session " + sessionId + " is already running";
    }

    // Stop a session
    public String stopSession(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null)
            return "No active session on charger " + chargerId;
        session.end();
        // publish a receipt for the completed session
        publishReceipt(session);
        activeSessionsByCharger.remove(chargerId);
        activeSessionsById.remove(session.getSessionId());
        return "Session stopped: " + session.getSessionId();
    }

    // Plug in / unplug
    public String plugIn(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null) {
            // Do not auto-create sessions on plug. The CP must have an approved
            // session (created via startSession / charge request) before plugging
            // the vehicle.
            return "No approved session on charger " + chargerId
                    + ". Request a charge (startSession) before plugging in.";
        }

        String status = session.getStatus();
        // Only allow plug if session is waiting (HOLD) or paused
        if ("HOLD".equals(status) || "PAUSED".equals(status)) {
            session.plugIn();
            addMessage("Charger " + chargerId + " plugged in. Session: " + session.getSessionId());
            return "Charger " + chargerId + " plugged in. Session is now " + session.getStatus();
        }

        if ("IN_PROGRESS".equals(status)) {
            return "Charger " + chargerId + " is already supplying (session " + session.getSessionId() + ").";
        }

        if ("COMPLETED".equals(status)) {
            return "Session " + session.getSessionId()
                    + " is already completed. Request a new session before plugging.";
        }

        return "Cannot plug in: session is in state '" + status + "'.";
    }

    public String unplug(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        // Treat unplug as session completion: end the session, publish receipt and
        // remove it so a subsequent plug creates a fresh new session.
        session.end();
        publishReceipt(session);
        activeSessionsByCharger.remove(chargerId);
        activeSessionsById.remove(session.getSessionId());
        addMessage("Charger " + chargerId + " unplugged and session completed: " + session.getSessionId());
        return "Charger " + chargerId + " unplugged. Session stopped: " + session.getSessionId();
    }

    // Pause / resume
    public String pause(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.pause();
        return "Paused session on charger " + chargerId;
    }

    public String resume(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.resume();
        return "Resumed session on charger " + chargerId;
    }

    public String pauseAll() {
        activeSessionsByCharger.values().forEach(ChargingSession::pause);
        return "All sessions paused.";
    }

    public String resumeAll() {
        activeSessionsByCharger.values().forEach(ChargingSession::resume);
        return "All sessions resumed.";
    }

    // Send telemetry
    public String sendTelemetry(String sessionId, double kWh, double power) {
        ChargingSession session = activeSessionsById.get(sessionId);
        if (session == null)
            return "No session found with ID " + sessionId;
        // The ChargingSession.addTelemetry(double,double) method does not exist,
        // so log the telemetry locally here (or update when ChargingSession supports
        // it).
        System.out.println("Telemetry for session " + sessionId + ": kWh=" + kWh + ", power=" + power);
        return "Telemetry received for session " + sessionId;
    }

    // Send telemetry by chargerId (finds session and delegates)
    public String sendTelemetryForCharger(String chargerId, double kWh, double power) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null)
            return "No active session on charger " + chargerId;
        return sendTelemetry(session.getSessionId(), kWh, power);
    }

    public String status(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.printStatus();
        return "Status printed for charger " + chargerId;
    }

    // Return the active ChargingSession object for a charger, or null if none.
    public ChargingSession getActiveSession(String chargerId) {
        return activeSessionsByCharger.get(chargerId);
    }

    // Stop all active sessions and remove them.
    public String stopAll() {
        List<ChargingSession> sessions = new ArrayList<>(activeSessionsByCharger.values());
        for (ChargingSession s : sessions) {
            s.end();
            publishReceipt(s);
        }
        activeSessionsByCharger.clear();
        activeSessionsById.clear();
        return "All sessions stopped.";
    }

    // Build and publish a simple JSON receipt to the broker and record in the
    // recent message buffer for UI visibility.
    private void publishReceipt(ChargingSession session) {
        if (session == null || kafkaSender == null)
            return;
        try {
            // Ensure energy is up to date
            session.updateEnergy();
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            sb.append("\"type\":\"receipt\"");
            sb.append(',').append("\"sessionId\":\"").append(escape(session.getSessionId())).append('\"');
            sb.append(',').append("\"chargerId\":\"").append(escape(session.getChargerId())).append('\"');
            sb.append(',').append("\"driverId\":\"").append(escape(session.getDriverId())).append('\"');
            sb.append(',').append("\"energy_kWh\":").append(String.format("%.6f", session.getEnergyConsumed()));
            sb.append(',').append("\"cost_eur\":").append(String.format("%.2f", session.getTotalCost()));
            sb.append('}');

            String msg = sb.toString();
            kafkaSender.send("charge_responses", msg);
            addMessage("OUT Receipt: " + msg);
        } catch (Exception e) {
            System.err.println("Failed to publish receipt: " + e.getMessage());
        }
    }

    private String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}