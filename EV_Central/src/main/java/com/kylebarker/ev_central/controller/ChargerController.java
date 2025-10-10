package com.kylebarker.ev_central.controller;

import com.kylebarker.ev_central.model.Charger;
import com.kylebarker.ev_central.repository.ChargerRepository;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.*;
import java.util.List;

@RestController
@RequestMapping("/api/central")
public class ChargerController {

    private final ChargerRepository repository;

    public ChargerController(ChargerRepository repository) {
        this.repository = repository;
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
}