package com.kylebarker.Controller;

import com.kylebarker.ChargingStation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("")
public class SessionController {

    @Autowired
    private ChargingStation station;

    // Update local state of the charging point
    @PostMapping("/cp/{cpUid}/state")
    public String updateState(@PathVariable String cpUid, @RequestParam String state) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        return station.updateState(cpUid, state);
    }

    // Read basic status about this CP (for UI)
    @GetMapping("/cp/{cpUid}/state")
    public ResponseEntity<?> readState(@PathVariable String cpUid) {
        if (!cpUid.equals(station.getChargerId())) {
            return ResponseEntity.status(403).body("This CP instance is configured as " + station.getChargerId()
                    + ". Request targeted " + cpUid + ".");
        }
        // Return a small JSON payload describing the station
        return ResponseEntity.ok(Map.of(
                "chargerId", station.getChargerId(),
                "activeSessions", station.getActiveSessionCount()));
    }

    // Return the configured charger id for this instance
    @GetMapping("/cp/self")
    public ResponseEntity<?> self() {
        return ResponseEntity.ok(Map.of("chargerId", station.getChargerId()));
    }

    // Submit a manual charge request from CP UI
    @PostMapping("/cp/{cpUid}/charge-requests")
    public String manualChargeRequest(@PathVariable String cpUid, @RequestParam String driverId) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        return station.startSession(cpUid, driverId); // Using startSession for manual requests
    }

    // Simulate plugging in a vehicle
    @PostMapping("/cp/{cpUid}/plug")
    public String plug(@PathVariable String cpUid) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        return station.plugIn(cpUid);
    }

    // Simulate unplugging a vehicle
    @PostMapping("/cp/{cpUid}/unplug")
    public String unplug(@PathVariable String cpUid) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        return station.unplug(cpUid);
    }

    // Start an authorized charging session
    @PostMapping("/cp/session/{sessionId}/start")
    public String startSession(@PathVariable String sessionId) {
        return station.startSessionById(sessionId);
    }

    // Send charging telemetry (kWh, power, etc.)
    @PostMapping("/cp/session/{sessionId}/telemetry")
    public String sendTelemetry(@PathVariable String sessionId,
            @RequestParam double kWh,
            @RequestParam double power) {
        return station.sendTelemetry(sessionId, kWh, power);
    }

    // Send telemetry by charger id (UI-friendly)
    @PostMapping("/cp/{cpUid}/telemetry")
    public String sendTelemetryByCharger(@PathVariable String cpUid,
            @RequestParam double kWh,
            @RequestParam double power) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        return station.sendTelemetryForCharger(cpUid, kWh, power);
    }

    // Stop a charging session locally
    @PostMapping("/cp/session/{cpUid}/stop")
    public String stopSession(@PathVariable String cpUid) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        return station.stopSession(cpUid);
    }
}