package com.kylebarker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

@Component
public class ChargingStation {
    private final Map<String, ChargingSession> activeSessions = new ConcurrentHashMap<>();
    private final Timer energyUpdater = new Timer(true);

    private final String chargerId;

    private String generateChargerId() {
        Random random = new Random();
        // Example: CHG-1000 to CHG-9999
        int number = 1000 + random.nextInt(9000);
        return "CHG-" + number;
    }

    public String getChargerId() {
        return chargerId;
    }

    // Accept charger.id from configuration (property or JVM arg). If not provided,
    // fall back to random.
    public ChargingStation(@Value("${charger.id:}") String configuredChargerId) {
        if (configuredChargerId != null && !configuredChargerId.isBlank()) {
            this.chargerId = configuredChargerId;
        } else {
            this.chargerId = generateChargerId();
        }

        System.out.println("Charging Station started with ID: " + chargerId);

        energyUpdater.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                activeSessions.values().forEach(ChargingSession::updateEnergy);
            }
        }, 0, 1000);
    }

    public String startSession(String chargerId, String driverId) {
        ChargingSession existing = activeSessions.get(chargerId);
        if (existing != null) {
            if (existing.getStatus().equals("PAUSED")) {
                existing.resume();
                return "Resumed paused session on charger " + chargerId;
            }
            return "Charger " + chargerId + " is already in use!";
        }

        ChargingSession session = new ChargingSession(chargerId, driverId);
        activeSessions.put(chargerId, session);
        return "Created new session (waiting for plug): " + session.getSessionId();
    }

    public String stopSession(String chargerId) {
        ChargingSession session = activeSessions.get(chargerId);
        if (session == null)
            return "No active session on charger " + chargerId;
        session.end();
        activeSessions.remove(chargerId);
        return "Session stopped: " + session.getSessionId();
    }

    public String plugIn(String chargerId) {
        ChargingSession session = activeSessions.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.plugIn();
        return "Charger " + chargerId + " plugged in. Session is now " + session.getStatus();
    }

    public String unplug(String chargerId) {
        ChargingSession session = activeSessions.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.unplug();
        return "Charger " + chargerId + " unplugged. Session is now " + session.getStatus();
    }

    public String pause(String chargerId) {
        ChargingSession session = activeSessions.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.pause();
        return "Paused session on charger " + chargerId;
    }

    public String resume(String chargerId) {
        ChargingSession session = activeSessions.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.resume();
        return "Resumed session on charger " + chargerId;
    }

    public String pauseAll() {
        activeSessions.values().forEach(ChargingSession::pause);
        return "All sessions paused.";
    }

    public String resumeAll() {
        activeSessions.values().forEach(ChargingSession::resume);
        return "All sessions resumed.";
    }

    public String status(String chargerId) {
        ChargingSession session = activeSessions.get(chargerId);
        if (session == null)
            return "No session on charger " + chargerId;
        session.printStatus();
        return "Status printed for charger " + chargerId;
    }
}