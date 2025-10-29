package com.kylebarker.ev_central.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylebarker.ev_central.model.Charger;
import com.kylebarker.ev_central.model.chargerState;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DriverRequestListener {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ChargerRepository chargerRepository;
    private final KafkaSender kafkaSender;

    public DriverRequestListener(ChargerRepository chargerRepository, KafkaSender kafkaSender) {
        this.chargerRepository = chargerRepository;
        this.kafkaSender = kafkaSender;
    }

    /**
     * Listen for driver charge requests on topic 'charge_requests'. Expected
     * payload:
     * { "requestId": "...", "driverId": 123, "cpUid": 10 }
     * We'll validate the charger is AVAILABLE (state ACTIVATED) before sending a
     * startCharging message to the CP topic. We also publish a simple response to
     * 'charge_responses' with ALLOW/DENY so the driver/service can be notified.
     */
    @KafkaListener(topics = "charge_requests", groupId = "central_charge_requests")
    public void listen(String message) {
        System.out.println("DriverRequestListener received: " + message);
        try {
            JsonNode json = mapper.readTree(message);
            String requestId = json.has("requestId") ? json.get("requestId").asText() : null;
            long driverId = json.has("driverId") ? json.get("driverId").asLong() : -1;
            long cpUid = json.has("cpUid") ? json.get("cpUid").asLong() : -1;

            if (cpUid < 0 || driverId < 0) {
                sendResponse(requestId, "DENY", "invalid_request");
                return;
            }

            Optional<Charger> opt = chargerRepository.findById(cpUid);
            if (opt.isEmpty()) {
                sendResponse(requestId, "DENY", "charger_not_found");
                return;
            }

            Charger charger = opt.get();
            chargerState state = charger.getState();

            // Log the resolved charger and state for easier debugging
            System.out.println("DriverRequestListener: found charger=" + charger + " state=" + state);

            // Explicitly handle null state
            if (state == null) {
                sendResponse(requestId, "DENY", "charger_state_unknown");
                return;
            }

            // Consider AVAILABLE only when ACTIVATED. If it's SUPPLYING or any other
            // state, deny and include the state in the reason so logs/clients can
            // diagnose quickly.
            if (state != chargerState.ACTIVATED) {
                sendResponse(requestId, "DENY", "charger_unavailable:" + state.name());
                return;
            }

            // Charger appears available — publish startCharging to CP topic
            // Build payload:
            // {"type":"startCharging","chargerId":"<cpUid>","driverId":"<driverId>"}
            ObjectNodeWrapper payload = new ObjectNodeWrapper();
            payload.put("type", "startCharging");
            payload.put("chargerId", String.valueOf(cpUid));
            payload.put("driverId", String.valueOf(driverId));

            kafkaSender.send("CP", payload.toString());
            sendResponse(requestId, "ALLOW", "queued");

        } catch (Exception e) {
            System.err.println("Failed to process driver request: " + e.getMessage());
        }
    }

    private void sendResponse(String requestId, String status, String reason) {
        try {
            ObjectNodeWrapper resp = new ObjectNodeWrapper();
            resp.put("requestId", requestId == null ? "" : requestId);
            resp.put("status", status);
            resp.put("reason", reason);
            kafkaSender.send("charge_responses", resp.toString());
        } catch (Exception e) {
            System.err.println("Failed to send charge response: " + e.getMessage());
        }
    }

    // Minimal utility wrapper to build a tiny JSON object without pulling in
    // additional
    // Spring Jackson types into this small example. Could be replaced with
    // ObjectNode.
    static class ObjectNodeWrapper {
        private final StringBuilder sb = new StringBuilder();

        public ObjectNodeWrapper() {
            sb.append('{');
        }

        public void put(String key, String value) {
            if (sb.length() > 1)
                sb.append(',');
            sb.append('"').append(escape(key)).append('"').append(':').append('"').append(escape(value)).append('"');
        }

        @Override
        public String toString() {
            return sb.append('}').toString();
        }

        private String escape(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
