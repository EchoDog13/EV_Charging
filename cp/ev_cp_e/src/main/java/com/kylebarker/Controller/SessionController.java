package com.kylebarker.Controller;

import com.kylebarker.ChargingStation;
import com.kylebarker.repository.KafkaSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("")
public class SessionController {

    @Autowired
    private ChargingStation station;

    @Autowired
    private KafkaSender kafkaSender;

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
        // Return a small JSON payload describing the station and state
        String state = station.getState(cpUid);
        Map<String, Object> base = Map.of(
                "chargerId", station.getChargerId(),
                "state", state,
                "activeSessions", station.getActiveSessionCount());

        // If supplying, include live telemetry and driver id
        if ("supplying".equals(state)) {
            var session = station.getActiveSession(cpUid);
            if (session != null) {
                Map<String, Object> supplyDetails = Map.of(
                        "driverId", session.getDriverId(),
                        "energy_kWh", session.getEnergyConsumed(),
                        "cost_eur", session.getTotalCost(),
                        "power_kW", session.getPowerKw());
                // Merge base map and supplyDetails
                var merged = new java.util.HashMap<String, Object>(base);
                merged.putAll(supplyDetails);
                return ResponseEntity.ok(merged);
            }
        }
        return ResponseEntity.ok(base);
    }

    // Return recent messages captured by this CP instance for UI diagnostics
    @GetMapping("/api/cp/{cpUid}/messages")
    public ResponseEntity<?> getMessages(@PathVariable String cpUid) {
        if (!cpUid.equals(station.getChargerId())) {
            return ResponseEntity.status(403).body("This CP instance is configured as " + station.getChargerId()
                    + ". Request targeted " + cpUid + ".");
        }
        return ResponseEntity.ok(station.getRecentMessages());
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
        // Build a startCharging request expected by central and send as an object
        try {
            Map<String, String> payload = Map.of(
                    "type", "startCharging",
                    "cpUid", cpUid,
                    "driverId", driverId);
            // Send the payload as an object so the Kafka JSON serializer writes
            // a JSON object rather than a quoted string.
            kafkaSender.send("charge_requests", payload);
            // Serialize for UI/logging
            ObjectMapper mapper = new ObjectMapper();
            String msg = mapper.writeValueAsString(payload);
            station.addMessage("OUT CP->central: " + msg);
            return "Charge request published: " + msg;
        } catch (Exception e) {
            return "Failed to publish charge request: " + e.getMessage();
        }
    }

    private String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Simulate plugging in a vehicle
    @PostMapping("/cp/{cpUid}/plug")
    public String plug(@PathVariable String cpUid) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        try {
            Map<String, String> payload = Map.of(
                    "type", "plugIn",
                    "chargerId", cpUid);
            kafkaSender.send("CP", payload);
            station.addMessage("OUT CP: plugIn -> " + cpUid);
            return "Plug instruction published to CP topic";
        } catch (Exception e) {
            return "Failed to publish plug instruction: " + e.getMessage();
        }
    }

    // Simulate unplugging a vehicle
    @PostMapping("/cp/{cpUid}/unplug")
    public String unplug(@PathVariable String cpUid) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        try {
            Map<String, String> payload = Map.of(
                    "type", "unplug",
                    "chargerId", cpUid);
            kafkaSender.send("CP", payload);
            station.addMessage("OUT CP: unplug -> " + cpUid);
            return "Unplug instruction published to CP topic";
        } catch (Exception e) {
            return "Failed to publish unplug instruction: " + e.getMessage();
        }
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

    @PostMapping("central/cps/{cpUid}/resume")
    public String centralResume(@PathVariable String cpUid) {
        if (!cpUid.equals(station.getChargerId())) {
            return "This CP instance is configured as " + station.getChargerId() + ". Request targeted " + cpUid + ".";
        }
        return station.updateState(cpUid, "ACTIVATED");
    }
}