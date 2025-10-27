package com.kylebarker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

@Component
public class ChargingStation {

    private final Map<String, ChargingSession> activeSessionsByCharger = new ConcurrentHashMap<>();
    private final Map<String, ChargingSession> activeSessionsById = new ConcurrentHashMap<>();
    private final Timer energyUpdater = new Timer(true);

    private final String chargerId;

    // Charger ID is now required to be supplied via configuration; no
    // auto-generation.

    public String getChargerId() {
        return chargerId;
    }

    // Expose current active session count for UI/status endpoints
    public int getActiveSessionCount() {
        return activeSessionsByCharger.size();
    }

    public ChargingStation(@Value("${charger.id:}") String configuredChargerId) {
        if (configuredChargerId == null || configuredChargerId.isBlank()) {
            throw new IllegalStateException(
                    "Required property 'charger.id' is missing or blank. Set via -Dcharger.id=... or CHARGER_ID env.");
        }
        this.chargerId = configuredChargerId;

        System.out.println("Charging Station started with ID: " + chargerId + " (configured)");

        energyUpdater.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                activeSessionsByCharger.values().forEach(ChargingSession::updateEnergy);
            }
        }, 0, 1000);
    }

    // Update local state of the charging point
    public String updateState(String chargerId, String state) {
        // Example: just log for now
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
        activeSessionsByCharger.remove(chargerId);
        activeSessionsById.remove(session.getSessionId());
        return "Session stopped: " + session.getSessionId();
    }

    // Plug in / unplug
    public String plugIn(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.plugIn();
        return "Charger " + chargerId + " plugged in. Session is now " + session.getStatus();
    }

    public String unplug(String chargerId) {
        ChargingSession session = activeSessionsByCharger.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.unplug();
        return "Charger " + chargerId + " unplugged. Session is now " + session.getStatus();
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
}