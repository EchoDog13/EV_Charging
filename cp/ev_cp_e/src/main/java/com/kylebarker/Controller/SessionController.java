package com.kylebarker.Controller;

import com.kylebarker.ChargingStation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SessionController {

    @Autowired
    private ChargingStation station;

    // Update local state of the charging point
    @PostMapping("/cp/{cpUid}/state")
    public String updateState(@PathVariable String cpUid, @RequestParam String state) {
        return station.updateState(cpUid, state); // You'll need to implement updateState in ChargingStation
    }

    // Submit a manual charge request from CP UI
    @PostMapping("/cp/{cpUid}/charge-requests")
    public String manualChargeRequest(@PathVariable String cpUid, @RequestParam String driverId) {
        return station.startSession(cpUid, driverId); // Using startSession for manual requests
    }

    // Simulate plugging in a vehicle
    @PostMapping("/cp/{cpUid}/plug")
    public String plug(@PathVariable String cpUid) {
        return station.plugIn(cpUid);
    }

    // Simulate unplugging a vehicle
    @PostMapping("/cp/{cpUid}/unplug")
    public String unplug(@PathVariable String cpUid) {
        return station.unplug(cpUid);
    }

    // Start an authorized charging session
    @PostMapping("/cp/session/{sessionId}/start")
    public String startSession(@PathVariable String sessionId) {
        // Your ChargingStation currently starts sessions by chargerId, not sessionId
        // If sessionId maps to chargerId, you can use:
        return "Not implemented: map sessionId to chargerId";
    }

    // Send charging telemetry (kWh, power, etc.)
    @PostMapping("/cp/session/{sessionId}/telemetry")
    public String sendTelemetry(@PathVariable String sessionId,
            @RequestParam double kWh,
            @RequestParam double power) {
        // You would need to implement sendTelemetry in ChargingStation
        return "Not implemented: send telemetry for sessionId";
    }

    // Stop a charging session locally
    @PostMapping("/cp/session/{cpUid}/stop")
    public String stopSession(@PathVariable String cpUid) {
        return station.stopSession(cpUid);
    }
}