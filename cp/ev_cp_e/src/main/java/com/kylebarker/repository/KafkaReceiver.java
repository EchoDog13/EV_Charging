package com.kylebarker.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylebarker.ChargingStation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableAsync
public class KafkaReceiver {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ChargingStation station;
    private final KafkaSender kafkaSender;

    @Value("${charger.id}")
    private String localChargerId;

    @Autowired
    public KafkaReceiver(ChargingStation station, KafkaSender kafkaSender) {
        this.station = station;
        this.kafkaSender = kafkaSender;
    }

    /* ---------- Listener – off-load immediately ---------- */
    @KafkaListener(topics = {"CP", "broadcast"}, groupId = "ev_central_group")
    public void listen(String raw) {
        processAsync(raw);
    }

    @Async("kafkaTaskExecutor")
    public void processAsync(String raw) {
        try {
            JsonNode json = parse(raw);
            String type = json.path("type").asText("unknown");

            station.addMessage("RECV: " + raw);

            // ---- GLOBAL COMMANDS (no chargerId required) ----
            if (List.of("stopAll", "pauseAll", "resumeAll").contains(type)) {
                handleGlobal(type);
                return;
            }

            // ---- LOCAL COMMANDS ----
            String chargerId = json.has("chargerId") ? json.get("chargerId").asText()
                             : json.has("cpUid") ? json.get("cpUid").asText() : null;

            if (chargerId == null || !chargerId.equals(localChargerId)) {
                return;   // not for us
            }

            String driverId = json.path("driverId").asText("unknown");
            handleLocal(type, driverId, json);

        } catch (Exception e) {
            station.addMessage("ERROR: " + e.getMessage());
        }
    }

    private JsonNode parse(String msg) throws Exception {
        JsonNode node = mapper.readTree(msg);
        if (node.isTextual()) node = mapper.readTree(node.asText());
        return node;
    }

    private void handleLocal(String type, String driverId, JsonNode json) {
        switch (type) {
            case "startCharging" -> station.startSession(localChargerId, driverId);
            case "stopCharging" -> station.stopSession(localChargerId);
            case "plugIn" -> station.plugIn(localChargerId);
            case "unplug" -> station.unplug(localChargerId);
            case "pause" -> station.pause(localChargerId);
            case "resume" -> station.resume(localChargerId);
            case "state_change" -> station.updateState(localChargerId, json.path("state").asText());
            default -> station.addMessage("Unknown local type: " + type);
        }
    }

    private void handleGlobal(String type) {
        switch (type) {
            case "stopAll" -> station.stopAll();
            case "pauseAll" -> station.pauseAll();
            case "resumeAll" -> station.resumeAll();
        }
    }

    /* ---------- REST-style helpers (unchanged) ---------- */
    public String apiStartSession(String chargerId, String driverId) { return station.startSession(chargerId, driverId); }
    public String apiStopSession(String chargerId) { return station.stopSession(chargerId); }
    public String apiPlugIn(String chargerId) { return station.plugIn(chargerId); }
    public String apiUnplug(String chargerId) { return station.unplug(chargerId); }
    public String apiPause(String chargerId) { return station.pause(chargerId); }
    public String apiResume(String chargerId) { return station.resume(chargerId); }
    public String apiStatus(String chargerId) {
        var s = station.getActiveSession(chargerId);
        if (s == null) return "Charger " + chargerId + " idle.";
        return String.format("Charger %s — %s | %.3f kWh | $%.2f | %s",
                chargerId, s.getStatus(), s.getEnergyConsumed(),
                s.getTotalCost(), s.isChargerConnected());
    }
}