package com.kylebarker.ev_central.controller;

import com.kylebarker.ev_central.model.Charger;
import com.kylebarker.ev_central.repository.ChargerRepository;
import com.kylebarker.ev_central.repository.KafkaSender;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.*;
import java.util.List;

@SpringBootApplication(scanBasePackages = "com.kylebarker.ev_central")

@RestController
@RequestMapping("/api/central")
public class ChargerController {

    private final ChargerRepository repository;
    private final KafkaSender kafkaSender;

    public ChargerController(ChargerRepository repository, KafkaSender kafkaSender) {
        this.repository = repository;
        this.kafkaSender = kafkaSender;
    }

    @GetMapping("/cps")
    public List<Charger> getAllChargers() {
        return repository.findAll();
    }

    @PostMapping
    public Charger createCharger(@Valid @RequestBody Charger charger) {
        return repository.save(charger);
    }

    @GetMapping("/{id}")
    public Charger getCharger(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Charger not found"));
    }

    @PutMapping("/{id}")
    public Charger updateCharger(@PathVariable Long id, @RequestBody Charger updated) {
        return repository.findById(id).map(charger -> {
            charger.setPricePerKW(updated.getPricePerKW());
            charger.setLocation(updated.getLocation());
            charger.setState(updated.getState());
            return repository.save(charger);
        }).orElseThrow(() -> new RuntimeException("Charger not found"));
    }

    @DeleteMapping("/{id}")
    public void deleteCharger(@PathVariable Long id) {
        repository.deleteById(id);
    }

    @GetMapping("/test")
    public String test() {
        System.out.println("TEST endpoint called");
        return "Controller is working! Current time: " + System.currentTimeMillis();
    }

    // Stop individual charging session
    @PostMapping("/stop/{chargerId}")
    public String stopCharging(@PathVariable Long chargerId) {
        // send kafka message to cp to stop charging
        String topic = "stop_charging";
        Long payload = chargerId;
        // If you want to create a JSON object with a key-value pair:
        JSONObject response = new org.json.JSONObject();
        response.put("uid", chargerId);
        response.put("function", "stopCharging");

        kafkaSender.send("CP", response.toString());

        return "Stop command sent for charger " + chargerId;
    }
}