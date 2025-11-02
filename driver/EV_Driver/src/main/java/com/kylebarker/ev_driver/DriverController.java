package com.kylebarker.ev_driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylebarker.ev_driver.model.*;
import com.kylebarker.ev_driver.service.DriverService;
import com.kylebarker.ev_driver.service.KafkaProducerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin("*")
@RestController
@RequestMapping("/driver")
public class DriverController {

    private final DriverService svc;
    private final KafkaProducerService kafkaProducerService;

    public DriverController(DriverService svc, KafkaProducerService kafkaProducerService) {
        this.svc = svc;
        this.kafkaProducerService = kafkaProducerService;
    }

    // ------------------- DRIVER ENDPOINTS -------------------

    // GET /driver/cps
    @GetMapping("/cps")
    public List<ChargingPointDto> listCPs() {
        return svc.listChargingPoints();
    }

    // POST /driver/charge-requests/{cpUid}/driver/{driverId}
    @PostMapping("/charge-requests/{cpUid}/driver/{driverId}")
    public String manualChargeRequest(@PathVariable String cpUid, @PathVariable String driverId) {
        // Build a startCharging request expected by central and send as an object
        try {
            Map<String, String> payload = Map.of(
                    "type", "startCharging",
                    "cpUid", cpUid,
                    "driverId", driverId);
            // Send the payload as an object so the Kafka JSON serializer writes
            // a JSON object rather than a quoted string.
            kafkaProducerService.sendMessage("charge_requests", payload);
            // Serialize for UI/logging
            ObjectMapper mapper = new ObjectMapper();
            String msg = mapper.writeValueAsString(payload);
            return "Charge request published: " + msg;
        } catch (Exception e) {
            return "Failed to publish charge request: " + e.getMessage();
        }
    }

    // GET /driver/charge-requests/{requestId}
    @GetMapping("/charge-requests/{requestId}")
    public ChargeRequestStatusDto status(@PathVariable String requestId) {
        return svc.getChargeRequestStatus(requestId);
    }

    // GET /driver/sessions/{sessionId}
    @GetMapping("/sessions/{sessionId}")
    public SessionDto session(@PathVariable String sessionId) {
        return svc.getSession(sessionId);
    }

    // POST /driver/sessions/{cpUid}/stop
    @PostMapping("/sessions/{cpUid}/stop")
    public String unplug(@PathVariable String cpUid) {
        try {
            Map<String, String> payload = Map.of(
                    "type", "unplug",
                    "cpUid", cpUid);
            kafkaProducerService.sendMessage("CP", payload);

            return "Unplug instruction published to CP topic";
        } catch (Exception e) {
            return "Failed to publish unplug instruction: " + e.getMessage();
        }
    }

    // POST /driver/simulations/start
    @PostMapping("/simulations/start")
    public ResponseEntity<SimulationStatusDto> startSim(@RequestBody SimulationStartDto body) {
        return ResponseEntity.ok(svc.startSimulation(body));
    }

    // POST /driver/simulations/stop
    @PostMapping("/simulations/stop")
    public ResponseEntity<SimulationStatusDto> stopSim() {
        return ResponseEntity.ok(svc.stopSimulation());
    }

    // GET /driver/tickets/{sessionId}
    @GetMapping("/tickets/{sessionId}")
    public TicketDto ticket(@PathVariable String sessionId) {
        return svc.getTicket(sessionId);
    }

    // ------------------- TEST KAFKA CONNECTION -------------------

    // POST /driver/test-kafka?msg=hello
    @PostMapping("/test-kafka")
    public String testKafka(@RequestParam String msg) {
        kafkaProducerService.sendMessage("charge_requests", msg);
        return "Message sent to Kafka: " + msg;
    }

    // POST /driver/test-message
    @PostMapping("/test-message")
    public ResponseEntity<String> testMessage() {
        kafkaProducerService.sendMessage("charge_requests", "Test message from driver");
        return ResponseEntity.ok("Message sent!");
    }
}
