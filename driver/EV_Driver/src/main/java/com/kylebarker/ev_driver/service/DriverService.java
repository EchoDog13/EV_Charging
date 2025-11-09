package com.kylebarker.ev_driver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kylebarker.ev_driver.model.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DriverService {
    private final Map<String, ChargeRequestStatusDto> requests = new ConcurrentHashMap<>();
    private final Map<String, SessionDto> sessions = new ConcurrentHashMap<>();
    private final Map<String, TicketDto> tickets = new ConcurrentHashMap<>();
    private final List<ChargingPointDto> cps = new CopyOnWriteArrayList<>();
    private final List<String> messages = new CopyOnWriteArrayList<>();

    private final String centralIp = "127.0.0.1";
    private final int centralPort = 5500;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> simTask;
    private final AtomicBoolean simRunning = new AtomicBoolean(false);

    private final ObjectMapper mapper = new ObjectMapper();

    public DriverService() {
        cps.add(new ChargingPointDto(1L, "MAD2", "C/ Serrano 10", 0.6, "ACTIVATED", System.currentTimeMillis()));
        cps.add(new ChargingPointDto(2L, "SEV3", "C/ Sevilla 4", 0.54, "STOPPED", System.currentTimeMillis()));
        cps.add(new ChargingPointDto(3L, "VAL1", "San Javier", 0.48, "ACTIVATED", System.currentTimeMillis()));
    }

    public List<ChargingPointDto> listChargingPoints() {
        return cps;

    }

    public ChargeRequestCreatedDto createChargeRequest(CreateChargeRequestDto body) {
        String reqId = UUID.randomUUID().toString();
        long driverId = body.getDriverId();
        long cpUid = body.getCpUid();

        requests.put(reqId, new ChargeRequestStatusDto(reqId, "PENDING", driverId, cpUid, null));
        CompletableFuture.runAsync(() -> sendToCentral(reqId, driverId, cpUid));
        return new ChargeRequestCreatedDto(reqId);
    }

    public ChargeRequestStatusDto getChargeRequestStatus(String requestId) {
        return requests.get(requestId);
    }

    public SessionDto getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public SessionDto stopSession(String sessionId) {
        SessionDto s = getSession(sessionId);
        if (!"ACTIVE".equals(s.getStatus()))
            return s;
        s.setStatus("STOPPED");
        s.setEndedAt(System.currentTimeMillis());
        tickets.put(s.getSessionId(),
                new TicketDto(s.getSessionId(), s.getDriverId(), s.getCpUid(),
                        s.getKwh(), s.getEuro(), s.getStartedAt(), s.getEndedAt()));
        return s;
    }

    public SimulationStatusDto startSimulation(SimulationStartDto dto) {
        if (simRunning.get())
            return new SimulationStatusDto(true, "already_running");
        File file = new File(dto.getFilePath());
        if (!file.exists())
            throw new RuntimeException("file not found");

        simRunning.set(true);
        simTask = scheduler.scheduleAtFixedRate(new Runnable() {
            BufferedReader br;
            {
                try {
                    br = new BufferedReader(new FileReader(file));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void run() {
                try {
                    String line = br.readLine();
                    if (line == null) {
                        stopSimulation();
                        br.close();
                        return;
                    }
                    line = line.trim();
                    if (line.isEmpty())
                        return;
                    long cp = Long.parseLong(line);
                    createChargeRequest(new CreateChargeRequestDto(dto.getDriverId(), cp));
                } catch (Exception e) {
                    stopSimulation();
                }
            }
        }, 0, 4, TimeUnit.SECONDS);
        return new SimulationStatusDto(true, "started");
    }

    public SimulationStatusDto stopSimulation() {
        if (simTask != null)
            simTask.cancel(true);
        simRunning.set(false);
        return new SimulationStatusDto(false, "stopped");
    }

    public TicketDto getTicket(String sessionId) {
        return tickets.get(sessionId);
    }

    private void sendToCentral(String reqId, long driverId, long cpUid) {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(centralIp, centralPort), 3000);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8),
                    true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));

            ObjectNode req = mapper.createObjectNode();
            req.put("function", "charge_request");
            req.put("driverId", driverId);
            req.put("cpUid", cpUid);
            req.put("requestId", reqId);
            req.put("timestamp", System.currentTimeMillis());
            out.println(req.toString());
            out.flush();

            String line = in.readLine();
            if (line != null) {
                ObjectNode node = (ObjectNode) mapper.readTree(line);
                if ("supply_auth".equals(node.path("function").asText())) {
                    String st = node.path("status").asText("DENY");
                    ChargeRequestStatusDto cur = requests.get(reqId);
                    if (cur != null) {
                        cur.setStatus(st);
                        if ("ALLOW".equals(st)) {
                            String sessionId = UUID.randomUUID().toString();
                            cur.setSessionId(sessionId);
                            sessions.put(sessionId, new SessionDto(
                                    sessionId, driverId, cpUid, "ACTIVE", System.currentTimeMillis(),
                                    null, 0.0, 0.0));
                        }
                    }
                }
            }
        } catch (Exception e) {
            ChargeRequestStatusDto cur = requests.get(reqId);
            if (cur != null)
                cur.setStatus("ERROR");
        }
    }

    public void addMessage(String message) {
        String normalized = normalizeMessage(message);
        if (!messages.contains(normalized)) {
            messages.add(normalized);
        }
    }

    private String normalizeMessage(String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object obj = mapper.readValue(message, Object.class);
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return message;
        }
    }

    public List<String> getMessages() {
        return new ArrayList<>(messages);
    }

    /**
     * Return messages that either do not contain a driverId, or contain a driverId matching
     * the provided string. This performs a best-effort JSON parse to inspect driverId keys.
     */
    public List<String> getMessagesForDriver(String driverId) {
        if (driverId == null) return getMessages();
        String target = driverId.trim();
        List<String> out = new ArrayList<>();
        for (String m : messages) {
            // try to parse as JSON and check for driverId/driverid
            boolean matched = false;
            try {
                Object obj = mapper.readValue(m, Object.class);
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    Object v = map.get("driverId");
                    if (v == null) v = map.get("driverid");
                    if (v != null) {
                        String sval = String.valueOf(v);
                        if (sval.equals(target)) matched = true;
                        else matched = false;
                    } else {
                        // No driverId in payload -> include
                        matched = true;
                    }
                } else {
                    // parsed but not a map -> include
                    matched = true;
                }
            } catch (Exception e) {
                // Not JSON — perform a simple substring check for "driverId":
                String lower = m.toLowerCase();
                if (lower.contains("driverid") || lower.contains("driverid")) {
                    // crude extraction of digits/word after driverId
                    String found = extractDriverIdFromText(m);
                    if (found != null) matched = found.equals(target);
                    else matched = false;
                } else {
                    // no driverId present -> include
                    matched = true;
                }
            }
            if (matched) out.add(m);
        }
        return out;
    }

    // crude helper: find first sequence of digits following driverId or "driverId" key
    private String extractDriverIdFromText(String text) {
        if (text == null) return null;
        try {
            String lower = text.toLowerCase();
            int idx = lower.indexOf("driverid");
            if (idx < 0) return null;
            // look for colon or = after key
            int after = idx + "driverid".length();
            // substring from after to maybe JSON or value
            String sub = text.substring(after);
            // find digits in substring
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(sub);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
        }
        return null;
    }
}
