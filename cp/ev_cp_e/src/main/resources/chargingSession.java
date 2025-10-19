package com.kylebarker;

import java.rmi.server.UID;

public class ChargingSession {
    private final UID sessionId;
    private final String chargerId;
    private final String driverId;
    private final double costPerKWh = 0.20; // Example cost per kWh
    private final double powerKw = 7.0; // Assume charger provides 7 kW
    private long startTime;
    private long pauseTime;
    private long totalPausedDuration;
    private long endTime;
    private double energyConsumed; // in kWh
    private String status; // IN_PROGRESS, PAUSED, COMPLETED
    private boolean chargerConnected;

    public ChargingSession(String chargerId, String driverId) {
        this.sessionId = new UID();
        this.chargerId = chargerId;
        this.driverId = driverId;
        this.startTime = System.currentTimeMillis();
        this.totalPausedDuration = 0;
        this.status = "IN_PROGRESS";
        this.chargerConnected = true;
        this.energyConsumed = 0.0;
    }

    public String getSessionId() {
        return sessionId.toString();
    }

    public String getChargerId() {
        return chargerId;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isChargerConnected() {
        return chargerConnected;
    }

    public double getTotalCost() {
        return energyConsumed * costPerKWh;
    }

    public double getEnergyConsumed() {
        return energyConsumed;
    }

    // ========== Charging simulation ==========

    public void updateEnergy() {
        if (status.equals("IN_PROGRESS") && chargerConnected) {
            long currentTime = System.currentTimeMillis();
            long effectiveTime = currentTime - startTime - totalPausedDuration;
            energyConsumed = (effectiveTime / 1000.0 / 3600.0) * powerKw; // kWh
        }
    }

    public void pause() {
        if (status.equals("IN_PROGRESS")) {
            status = "PAUSED";
            pauseTime = System.currentTimeMillis();
            System.out.println("Session " + sessionId + " paused.");
        }
    }

    public void resume() {
        if (status.equals("PAUSED")) {
            status = "IN_PROGRESS";
            totalPausedDuration += System.currentTimeMillis() - pauseTime;
            System.out.println("Session " + sessionId + " resumed.");
        }
    }

    public void end() {
        updateEnergy();
        endTime = System.currentTimeMillis();
        status = "COMPLETED";
        System.out.println("Session " + sessionId + " completed. Energy: " + String.format("%.2f", energyConsumed) +
                " kWh, Cost: $" + String.format("%.2f", getTotalCost()));
    }

    public void plugIn() {
        chargerConnected = true;
        System.out.println("Charger " + chargerId + " plugged in.");
    }

    public void unplug() {
        chargerConnected = false;
        System.out.println("Charger " + chargerId + " unplugged.");
    }
}