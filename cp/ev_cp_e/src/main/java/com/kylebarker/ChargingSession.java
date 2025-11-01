package com.kylebarker;

import java.util.UUID;

public class ChargingSession {
    private final String sessionId;
    private final String chargerId;
    private final String driverId;
    private final double costPerKWh = 0.20;
    private long startTime;
    private long pausedTime;
    private long totalPausedDuration;
    private double energyConsumed;
    // Session-specific transient state is still tracked here for lifecycle
    // timing, but the canonical shared state is stored in
    // ChargingStation.GLOBAL_STATE.
    // We'll keep a localStatus for internal checks but expose/get/set via
    // ChargingStation.GLOBAL_STATE.
    private String localStatus; // HOLD, IN_PROGRESS, PAUSED, COMPLETED
    private boolean chargerConnected;
    private final double powerKw = 7.0; // example: 7 kW charger

    public ChargingSession(String chargerId, String driverId) {
        this.sessionId = UUID.randomUUID().toString();
        this.chargerId = chargerId;
        this.driverId = driverId;
        this.localStatus = "HOLD"; // waiting for plug
        this.chargerConnected = false;
        this.energyConsumed = 0.0;
        this.totalPausedDuration = 0;

        System.out.println("Created new session " + sessionId + " for charger " + chargerId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getChargerId() {
        return chargerId;
    }

    public String getDriverId() {
        return driverId;
    }

    public boolean isChargerConnected() {
        return chargerConnected;
    }

    public String getStatus() {
        // Return the global state if it's set to a known session-level value,
        // otherwise fall back to the session's localStatus.
        try {
            String g = ChargingStation.GLOBAL_STATE;
            if (g != null && !g.isBlank()) {
                return g;
            }
        } catch (Exception ignored) {
        }
        return localStatus;
    }

    public double getEnergyConsumed() {
        updateEnergy();
        return energyConsumed;
    }

    public double getTotalCost() {
        return getEnergyConsumed() * costPerKWh;
    }

    public double getPowerKw() {
        return powerKw;
    }

    public void plugIn() {
        chargerConnected = true;
        if ("HOLD".equals(ChargingStation.GLOBAL_STATE)) {
            localStatus = "IN_PROGRESS";
            ChargingStation.GLOBAL_STATE = "IN_PROGRESS";
            startTime = System.currentTimeMillis();
            System.out.println("Charger " + chargerId + " plugged in and session started.");
        } else if ("PAUSED".equals(ChargingStation.GLOBAL_STATE)) {
            // resume accounting for paused duration
            resume();
            System.out.println("Charger " + chargerId + " re-plugged and session resumed.");
        } else {
            System.out.println("Charger " + chargerId + " plugged in.");
        }
    }

    public void unplug() {
        chargerConnected = false;
        if ("IN_PROGRESS".equals(ChargingStation.GLOBAL_STATE)) {
            pause();
        }
        System.out.println("Charger " + chargerId + " unplugged.");
    }

    public void pause() {
        if ("IN_PROGRESS".equals(ChargingStation.GLOBAL_STATE)) {
            localStatus = "PAUSED";
            ChargingStation.GLOBAL_STATE = "PAUSED";
            pausedTime = System.currentTimeMillis();
            System.out.println("Session " + sessionId + " paused.");
        }
    }

    public void resume() {
        if ("PAUSED".equals(ChargingStation.GLOBAL_STATE)) {
            localStatus = "IN_PROGRESS";
            ChargingStation.GLOBAL_STATE = "IN_PROGRESS";
            totalPausedDuration += System.currentTimeMillis() - pausedTime;
            System.out.println("Session " + sessionId + " resumed.");
        }
    }

    public void end() {
        updateEnergy();
        localStatus = "COMPLETED";
        ChargingStation.GLOBAL_STATE = "COMPLETED";
        System.out.println("Session " + sessionId + " completed. Energy: "
                + String.format("%.3f", energyConsumed) + " kWh, Cost: $"
                + String.format("%.2f", getTotalCost()));
    }

    public void updateEnergy() {
        if ("IN_PROGRESS".equals(ChargingStation.GLOBAL_STATE) && chargerConnected) {
            long currentTime = System.currentTimeMillis();
            long effectiveTime = currentTime - startTime - totalPausedDuration;
            energyConsumed = (effectiveTime / 1000.0 / 3600.0) * powerKw; // kWh
        }
    }

    public void printStatus() {
        updateEnergy();
        System.out.println("Session " + sessionId + " | Charger: " + chargerId
                + " | Status: " + getStatus()
                + " | Energy: " + String.format("%.3f", energyConsumed) + " kWh"
                + " | Cost: $" + String.format("%.2f", getTotalCost()));
    }

    public String getSessionInfo() {
        updateEnergy(); // make sure energy is up to date
        return "Session " + sessionId +
                " | Charger: " + chargerId +
                " | Driver: " + driverId +
                " | Status: " + getStatus() +
                " | Energy: " + String.format("%.3f", energyConsumed) + " kWh" +
                " | Cost: $" + String.format("%.2f", getTotalCost());
    }

    public void setState(String newState) {
        // set both local and global state
        this.localStatus = newState;
        try {
            ChargingStation.GLOBAL_STATE = newState;
        } catch (Exception ignored) {
        }
    }
}