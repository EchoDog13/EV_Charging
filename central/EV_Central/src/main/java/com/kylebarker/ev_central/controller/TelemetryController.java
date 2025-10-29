package com.kylebarker.ev_central.controller;

import com.kylebarker.ev_central.repository.CpTelemetryListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
public class TelemetryController {

    private final CpTelemetryListener listener;

    public TelemetryController(CpTelemetryListener listener) {
        this.listener = listener;
    }

    // Existing polling endpoint (keeps compatibility)
    @GetMapping("/central/telemetry/{cpUid}")
    public Map<String, Object> getLatest(@PathVariable String cpUid) {
        Map<String, Object> v = listener.getLatestFor(cpUid);
        if (v == null)
            return Map.of();
        return v;
    }

    // Server-Sent Events endpoint for real-time updates from Kafka
    @CrossOrigin(origins = "*")
    @GetMapping(path = "/central/telemetry/stream/{cpUid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetry(@PathVariable String cpUid) {
        return listener.register(cpUid);
    }
}
