package com.kylebarker.ev_central.controller;

import com.kylebarker.ev_central.model.Charger;
import com.kylebarker.ev_central.repository.ChargerRepository;
import com.kylebarker.ev_central.repository.KafkaSender;
import com.kylebarker.ev_central.repository.CpTelemetryListener;

import org.json.JSONObject;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import com.kylebarker.ev_central.model.chargerState;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import jakarta.validation.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

@SpringBootApplication(scanBasePackages = "com.kylebarker.ev_central")

@CrossOrigin("*")
@RestController
@RequestMapping("/central")
public class ChargerController {

    private final ChargerRepository repository;
    private final KafkaSender kafkaSender;
    private final CpTelemetryListener telemetryListener;

    // In-memory stores for demo purposes
    private final Map<String, JSONObject> chargeRequests = new ConcurrentHashMap<>();
    private final Map<String, JSONObject> sessions = new ConcurrentHashMap<>();
    private final Map<String, JSONObject> tickets = new ConcurrentHashMap<>();
    private final Map<String, JSONObject> messages = new ConcurrentHashMap<>();

    public ChargerController(ChargerRepository repository, KafkaSender kafkaSender,
            CpTelemetryListener telemetryListener) {
        this.repository = repository;
        this.kafkaSender = kafkaSender;
        this.telemetryListener = telemetryListener;
    }

    @GetMapping("/cps")
    public List<Charger> getAllChargers() {
        // Enrich persisted charger info with any recent telemetry so the dashboard
        // reflects up-to-date state (avoids SSE-updated DOM being overwritten by
        // stale DB values on the next poll).
        List<Charger> chargers = repository.findAll();
        for (Charger c : chargers) {
            try {
                String uid = String.valueOf(c.getUid());
                Map<String, Object> last = telemetryListener.getLatestFor(uid);
                if (last != null) {
                    // map may contain timestamp (number or string)
                    Object ts = last.get("timestamp");
                    if (ts == null)
                        ts = last.get("time");
                    if (ts != null) {
                        try {
                            long l = 0L;
                            if (ts instanceof Number)
                                l = ((Number) ts).longValue();
                            else
                                l = Long.parseLong(String.valueOf(ts));
                            c.setLastHealthCheck(l);
                        } catch (Exception ignore) {
                        }
                    }
                    // prefer explicit state from telemetry, otherwise treat telemetry
                    // presence as SUPPLYING
                    Object st = last.get("state");
                    if (st != null) {
                        try {
                            c.setState(com.kylebarker.ev_central.model.chargerState
                                    .valueOf(String.valueOf(st).toUpperCase()));
                        } catch (IllegalArgumentException ignore) {
                        }
                    } else if (last.get("sessionId") != null || last.get("energy_kWh") != null
                            || last.get("power_kW") != null) {
                        c.setState(com.kylebarker.ev_central.model.chargerState.SUPPLYING);
                    }
                }
            } catch (Exception ex) {
                // ignore enrichment failures per device
            }
        }
        return chargers;
    }

    @GetMapping("/cps/{cpUid}")
    public ResponseEntity<Charger> getChargerByUid(@PathVariable String cpUid) {
        try {
            Long uid = Long.parseLong(cpUid);
            return repository.findById(uid).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public Charger createCharger(@Valid @RequestBody Charger charger) {
        Charger saved = repository.save(charger);
        // record registration message
        JSONObject msg = new JSONObject();
        msg.put("type", "registration");
        msg.put("uid", saved.getUid());
        messages.put(UUID.randomUUID().toString(), msg);
        return saved;
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

    // Change charging point state (activate, stop, out-of-order)
    @PostMapping("/cps/{cpUid}/state")
    public ResponseEntity<String> setChargerState(@PathVariable String cpUid, @RequestParam String state) {
        ResponseEntity<Charger> resp = getChargerByUid(cpUid);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(resp.getStatusCode()).body("Charger not found");
        }
        Long uid = Long.parseLong(cpUid);
        Charger c = repository.findById(uid).orElse(null);
        try {
            chargerState cs = chargerState.valueOf(state.toUpperCase());
            if (c == null)
                return ResponseEntity.notFound().build();
            c.setState(cs);
            repository.save(c);
            JSONObject msg = new JSONObject();
            msg.put("type", "state_change");
            msg.put("uid", c.getUid());
            msg.put("state", cs.name());
            messages.put(UUID.randomUUID().toString(), msg);
            kafkaSender.send("CP", msg.toString());
            return ResponseEntity.ok("State updated");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid state: " + state);
        }
    }

    // Stop all charging points at once
    @PostMapping("/cps/commands/stop-all")
    public ResponseEntity<String> stopAllChargers() {
        // Persist OUT_OF_ORDER state for all chargers so the dashboard shows RED/Out of
        // Service
        List<Charger> all = repository.findAll();
        for (Charger c : all) {
            try {
                // don't mark disconnected chargers as out-of-order
                if (c.getState() == null || c.getState() == chargerState.DISCONNECTED)
                    continue;
                c.setState(chargerState.OUT_OF_ORDER);
                repository.save(c);
                JSONObject m = new JSONObject();
                m.put("type", "state_change");
                m.put("uid", c.getUid());
                m.put("state", chargerState.OUT_OF_ORDER.name());
                messages.put(UUID.randomUUID().toString(), m);
                kafkaSender.send("CP", m.toString());
            } catch (Exception ex) {
                // ignore per-device failures
            }
        }
        // also publish a generic stopAll command for CP fleet
        JSONObject cmd = new JSONObject();
        cmd.put("type", "stopAll");
        kafkaSender.send("CP", cmd.toString());
        messages.put(UUID.randomUUID().toString(), cmd);
        return ResponseEntity.ok("Stop all command sent and persisted");
    }

    // Start all charging points at once (publishes a startAll message)
    @PostMapping("/cps/commands/start-all")
    public ResponseEntity<String> startAllChargers() {
        JSONObject msg = new JSONObject();
        msg.put("type", "startAll");
        kafkaSender.send("CP", msg.toString());
        messages.put(UUID.randomUUID().toString(), msg);
        return ResponseEntity.ok("Start all command sent");
    }

    // Authorize a driver's charge request
    @PostMapping("/charge-requests")
    public ResponseEntity<String> authorizeChargeRequest(@RequestParam String driverId, @RequestParam String cpUid) {
        String reqId = UUID.randomUUID().toString();
        JSONObject req = new JSONObject();
        req.put("requestId", reqId);
        req.put("driverId", driverId);
        req.put("cpUid", cpUid);
        req.put("status", "AUTHORIZED");
        chargeRequests.put(reqId, req);
        // send message to CP to start charging
        JSONObject msg = new JSONObject();
        msg.put("type", "startCharging");
        msg.put("chargerId", cpUid);
        msg.put("driverId", driverId);
        kafkaSender.send("CP", msg.toString());
        messages.put(UUID.randomUUID().toString(), msg);
        return ResponseEntity.ok(reqId);
    }

    @GetMapping("/charge-requests/{requestId}")
    public ResponseEntity<JSONObject> getChargeRequest(@PathVariable String requestId) {
        JSONObject r = chargeRequests.get(requestId);
        if (r == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(r);
    }

    // Sessions endpoints (simple in-memory)
    @GetMapping("/sessions")
    public List<JSONObject> listSessions() {
        return sessions.values().stream().collect(Collectors.toList());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<JSONObject> getSession(@PathVariable String sessionId) {
        JSONObject s = sessions.get(sessionId);
        if (s == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(s);
    }

    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<String> endSession(@PathVariable String sessionId) {
        JSONObject s = sessions.get(sessionId);
        if (s == null)
            return ResponseEntity.notFound().build();
        s.put("status", "ENDED");
        // generate ticket
        JSONObject ticket = new JSONObject();
        ticket.put("sessionId", sessionId);
        ticket.put("amount", 0.0);
        tickets.put(sessionId, ticket);
        JSONObject msg = new JSONObject();
        msg.put("type", "endSession");
        msg.put("sessionId", sessionId);
        kafkaSender.send("CP", msg.toString());
        messages.put(UUID.randomUUID().toString(), msg);
        return ResponseEntity.ok("Session ended");
    }

    @GetMapping("/tickets/{sessionId}")
    public ResponseEntity<JSONObject> getTicket(@PathVariable String sessionId) {
        JSONObject t = tickets.get(sessionId);
        if (t == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(t);
    }

    @GetMapping("/messages")
    public List<JSONObject> listMessages() {
        return messages.values().stream().collect(Collectors.toList());
    }

    @GetMapping("/test")
    public String test() {
        System.out.println("TEST endpoint called");
        return "Controller is working! Current time: " + System.currentTimeMillis();
    }

    // Stop individual charging session
    @PostMapping("/stop/{chargerId}")
    public String stopCharging(@PathVariable Long chargerId) {
        JSONObject response = new JSONObject();
        response.put("type", "OUT_OF_ORDER");
        response.put("uid", chargerId);

        // Persist OUT_OF_ORDER only if the charger is present and not DISCONNECTED
        repository.findById(chargerId).ifPresent(ch -> {
            if (ch.getState() != null && ch.getState() != chargerState.DISCONNECTED) {
                ch.setState(chargerState.OUT_OF_ORDER);
                repository.save(ch);
                JSONObject m = new JSONObject();
                m.put("type", "state_change");
                m.put("uid", ch.getUid());
                m.put("state", chargerState.OUT_OF_ORDER.name());
                messages.put(UUID.randomUUID().toString(), m);
                kafkaSender.send("broadcast", m.toString());
            }
        });

        kafkaSender.send("broadcast", response.toString());
        messages.put(UUID.randomUUID().toString(), response);
        return "Stop command sent for charger " + chargerId;
    }

    // Stop individual charging session (new: publishes 'chargerId' and optional
    // driverId)
    @PostMapping("/cps/{cpUid}/stop")
    public ResponseEntity<String> stopChargingWithDriver(@PathVariable String cpUid,
            @RequestParam(required = false) String driverId) {
        // Persist OUT_OF_ORDER for this charger and notify CP
        JSONObject msg = new JSONObject();
        msg.put("type", "stopCharging");
        msg.put("chargerId", cpUid);
        if (driverId != null && !driverId.isBlank()) {
            msg.put("driverId", driverId);
        }
        try {
            Long uid = Long.parseLong(cpUid);
            repository.findById(uid).ifPresent(ch -> {
                if (ch.getState() == null || ch.getState() == chargerState.DISCONNECTED)
                    return;
                ch.setState(chargerState.OUT_OF_ORDER);
                repository.save(ch);
                JSONObject m = new JSONObject();
                m.put("type", "state_change");
                m.put("uid", ch.getUid());
                m.put("state", chargerState.STOPPED.name());
                messages.put(UUID.randomUUID().toString(), m);
                kafkaSender.send("broadcast", m.toString());
            });
        } catch (NumberFormatException ignore) {
        }
        messages.put(UUID.randomUUID().toString(), msg);
        return ResponseEntity.ok("Stop command sent for charger " + cpUid + " and state persisted");
    }

    // Resume individual charging point (set to ACTIVATED)
    @PostMapping("/cps/{cpUid}/resume")
    public ResponseEntity<String> resumeCharging(@PathVariable String cpUid) {
        try {
            Long uid = Long.parseLong(cpUid);
            repository.findById(uid).ifPresent(ch -> {
                ch.setState(chargerState.ACTIVATED);
                repository.save(ch);
                JSONObject m = new JSONObject();
                m.put("type", "state_change");
                m.put("uid", ch.getUid());
                m.put("state", chargerState.ACTIVATED.name());
                messages.put(UUID.randomUUID().toString(), m);
                kafkaSender.send("broadcast", m.toString());
            });
            return ResponseEntity.ok("Resume command sent for charger " + cpUid);
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body("Invalid charger id");
        }
    }

    // Resume all charging points (set all to ACTIVATED)
    @PostMapping("/cps/commands/resume-all")
    public ResponseEntity<String> resumeAllChargers() {
        List<Charger> all = repository.findAll();
        for (Charger c : all) {
            try {
                c.setState(chargerState.ACTIVATED);
                repository.save(c);
                JSONObject m = new JSONObject();
                m.put("type", "state_change");
                m.put("uid", c.getUid());
                m.put("state", chargerState.ACTIVATED.name());
                messages.put(UUID.randomUUID().toString(), m);
                kafkaSender.send("CP", m.toString());
            } catch (Exception ex) {
            }
        }
        JSONObject cmd = new JSONObject();
        cmd.put("type", "startAll");
        kafkaSender.send("CP", cmd.toString());
        messages.put(UUID.randomUUID().toString(), cmd);
        return ResponseEntity.ok("Resume all command sent and persisted");
    }
}