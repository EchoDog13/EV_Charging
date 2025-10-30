package com.kylebarker.ev_driver;

import com.kylebarker.ev_driver.model.*;
import com.kylebarker.ev_driver.service.DriverService;
import com.kylebarker.ev_driver.service.KafkaProducerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // POST /driver/charge-requests
    @PostMapping("/charge-requests")
    public ResponseEntity<ChargeRequestCreatedDto> create(@RequestBody CreateChargeRequestDto body) {
        // create the charge request via service
        ChargeRequestCreatedDto created = svc.createChargeRequest(body);

        // build JSON payload expected by central system / Kafka consumer
        String payload = String.format("{\"cpUid\":\"%d\",\"type\":\"startCharging\",\"driverId\":\"%d\"}",
                body.getCpUid(), body.getDriverId());

        // send to Kafka topic
        kafkaProducerService.sendMessage("charge_requests", payload);

        return ResponseEntity.ok(created);
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

    // POST /driver/sessions/{sessionId}/stop
    @PostMapping("/sessions/{sessionId}/stop")
    public SessionDto stop(@PathVariable String sessionId) {
        return svc.stopSession(sessionId);
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
