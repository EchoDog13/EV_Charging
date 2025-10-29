package com.kylebarker.ev_central.repository;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CpTelemetryListener {

    // Store latest telemetry per cpUid for quick dashboard access
    private final Map<String, Map<String, Object>> latest = new ConcurrentHashMap<>();

    // SSE emitters per cpUid
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "cp_telemetry", groupId = "central_telemetry")
    public void listen(Object message) {
        try {
            System.out.println("Central received telemetry: " + message);

            Object payload = message;
            // If Spring gives us a ConsumerRecord wrapper, unwrap it
            if (message instanceof ConsumerRecord) {
                payload = ((ConsumerRecord<?, ?>) message).value();
            }

            Map<String, Object> map = null;

            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) payload;
                map = m;
            } else if (payload instanceof String) {
                try {
                    map = mapper.readValue((String) payload, new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception ex) {
                    System.err.println("Failed to parse telemetry JSON string: " + ex.getMessage());
                }
            } else {
                // Other types (e.g., byte[]) - try to convert
                try {
                    String asString = String.valueOf(payload);
                    map = mapper.readValue(asString, new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception ex) {
                    // give up
                }
            }

            if (map != null) {
                Object cp = map.get("cpUid");
                if (cp != null) {
                    String cpUid = String.valueOf(cp);
                    latest.put(cpUid, map);
                    // push to any registered SSE clients for this cpUid
                    pushToEmitters(cpUid, map);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to process telemetry: " + e.getMessage());
        }
    }

    public Map<String, Object> getLatestFor(String cpUid) {
        return latest.get(cpUid);
    }

    public SseEmitter register(String cpUid) {
        // no timeout (0L) so clients stay connected until they close
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(cpUid, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(cpUid, emitter));
        emitter.onTimeout(() -> removeEmitter(cpUid, emitter));

        // send last known value immediately if present
        Map<String, Object> last = latest.get(cpUid);
        if (last != null) {
            try {
                emitter.send(SseEmitter.event().name("telemetry").data(last));
            } catch (IOException e) {
                removeEmitter(cpUid, emitter);
            }
        }

        return emitter;
    }

    private void pushToEmitters(String cpUid, Map<String, Object> map) {
        List<SseEmitter> list = emitters.get(cpUid);
        if (list == null)
            return;

        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name("telemetry").data(map));
            } catch (IOException ex) {
                removeEmitter(cpUid, e);
            }
        }
    }

    private void removeEmitter(String cpUid, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(cpUid);
        if (list != null) {
            list.remove(emitter);
        }
    }

}
