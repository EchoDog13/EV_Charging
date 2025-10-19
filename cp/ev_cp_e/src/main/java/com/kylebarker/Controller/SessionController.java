package com.kylebarker.Controller;

import com.kylebarker.ChargingStation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    @Autowired
    private ChargingStation station;

    @PostMapping("/start")
    public String start(@RequestParam String chargerId, @RequestParam String driverId) {
        return station.startSession(chargerId, driverId);
    }

    @PostMapping("/stop")
    public String stop(@RequestParam String chargerId) {
        return station.stopSession(chargerId);
    }

    @PostMapping("/plugIn")
    public String plug(@RequestParam String chargerId) {
        return station.plugIn(chargerId);
    }

    @PostMapping("/unplug")
    public String unplug(@RequestParam String chargerId) {
        return station.unplug(chargerId);
    }

    @PostMapping("/pause")
    public String pause(@RequestParam String chargerId) {
        return station.pause(chargerId);
    }

    @PostMapping("/resume")
    public String resume(@RequestParam String chargerId) {
        return station.resume(chargerId);
    }

    @PostMapping("/pauseAll")
    public String pauseAll() {
        return station.pauseAll();
    }

    @PostMapping("/resumeAll")
    public String resumeAll() {
        return station.resumeAll();
    }

    @GetMapping("/status")
    public String status(@RequestParam String chargerId) {
        return station.status(chargerId);
    }
}