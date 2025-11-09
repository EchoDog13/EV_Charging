const $ = (s) => document.querySelector(s);
const tbody = $("#tbl tbody");

// API endpoints. The driver UI can fetch the list of charging points from the Central API
// by default. If your Central server runs on a different host/port, update CENTRAL_BASE
// accordingly (see notes below).
// Default CENTRAL_BASE: http://localhost:5500 (change if central is on a different host)
// In the browser we don't have process.env. Allow server/template to set
// window.CENTRAL_IP / window.CENTRAL_PORT, otherwise fall back to localhost.
const CENTRAL_IP =
  (typeof window !== "undefined" && window.CENTRAL_IP) || "http://100.83.66.30";
const CENTRAL_PORT =
  (typeof window !== "undefined" && window.CENTRAL_PORT) || "9900";
// Ensure CENTRAL_IP already contains protocol; if not, default to http://
const hasProto = /^https?:\/\//i.test(CENTRAL_IP);
const CENTRAL_BASE =
  (hasProto ? CENTRAL_IP : `http://${CENTRAL_IP}`) +
  (CENTRAL_PORT ? `:${CENTRAL_PORT}` : "");

// DRIVER API base: derive from window.DRIVER_API / #driverApi input. Default kept for backwards compatibility.
const DEFAULT_DRIVER_API = "100.74.162.58:7040";

function normalizeDriverApi(value) {
  if (!value) return null;
  value = String(value).trim();
  if (!value) return null;
  // If value already contains protocol, use as-is. Otherwise assume http://
  if (/^https?:\/\//i.test(value)) return value.replace(/\/$/, "");
  return `http://${value.replace(/\/$/, "")}`;
}

// Read from window global first (server-side template can set this), then from the input field.
function getDriverBase() {
  // prefer explicit window global
  if (typeof window !== "undefined" && window.DRIVER_API) {
    const v = normalizeDriverApi(window.DRIVER_API);
    if (v) return v;
  }
  // then prefer value in the UI input if present
  const input =
    document && document.getElementById && document.getElementById("driverApi");
  if (input && input.value) {
    const v = normalizeDriverApi(input.value);
    if (v) return v;
  }
  // fallback to default
  return normalizeDriverApi(DEFAULT_DRIVER_API);
}

// Allow runtime updates
function setDriverApi(value) {
  const norm =
    normalizeDriverApi(value) || normalizeDriverApi(DEFAULT_DRIVER_API);
  // store in window so other scripts can access if needed
  if (typeof window !== "undefined") window.DRIVER_API = norm;
  // update input value if present
  const input =
    document && document.getElementById && document.getElementById("driverApi");
  if (input) input.value = value || "";
  console.info("Driver API base set to", norm);
  return norm;
}

// Expose getter for other functions
function DRIVER_BASE() {
  return getDriverBase();
}

const API = {
  // Central's CPS endpoint (absolute URL) — used by the front-end to list available CPs
  cps: `${CENTRAL_BASE}/central/cps`,
  // make req a function so cpUid and driverId are interpolated into the path
  req: (cpUid, driverId) =>
    `/driver/charge-requests/${cpUid}/driver/${driverId}`,
  reqStatus: (id) => `/driver/charge-requests/${id}`,
  session: (id) => `/driver/sessions/${id}`,
  stop: (id) => `/driver/sessions/${id}/stop`,
  simStart: "/driver/simulations/start",
  simStop: "/driver/simulations/stop",
  ticket: (id) => `/driver/tickets/${id}`,
};

function pill(state) {
  return `<span class="pill ${state}">${state}</span>`;
}
function fmt(ts) {
  return ts ? new Date(ts).toLocaleString() : "—";
}
function jfmt(o) {
  return JSON.stringify(o, null, 2);
}

// Track displayed messages to avoid duplicates
const displayedMessages = new Set();

// Messages UI helpers (show recent kafka messages from Central relevant to this driver)
const maxMessages = 50;
function appendDriverMessage(text) {
  try {
    const container = document.getElementById("messages");
    if (!container) return;
    if (container.textContent === "No messages yet.")
      container.textContent = "";
    const el = document.createElement("div");
    el.className = "message";
    el.textContent = new Date().toLocaleTimeString() + " — " + text;
    container.prepend(el);
    while (container.children.length > maxMessages)
      container.removeChild(container.lastChild);
  } catch (e) {
    console.warn("appendDriverMessage failed", e);
  }
}

function isHealthCheckMessage(text) {
  if (!text) return false;
  const s = String(text).trim();
  // try JSON parse to inspect type
  try {
    const j = JSON.parse(s);
    if (j && typeof j === "object") {
      const t = j.type || j.function || j.msgType || j.messageType;
      if (t && String(t).toLowerCase().includes("health")) return true;
    }
  } catch (e) {
    // not JSON — treat short actuator-style messages as health checks
    const lower = s.toLowerCase();
    return (
      lower.includes("health") ||
      lower.includes("up") ||
      lower.includes("actuator")
    );
  }
  return false;
}

// Poll Driver's messages endpoint and show driver-relevant messages (exclude telemetry)
async function pollDriverMessages() {
  try {
    // include page driverId as optional query param so server can filter messages
    const pageDriverId = String(
      document.getElementById("driverId")?.value || ""
    );
    const url = pageDriverId
      ? `${DRIVER_BASE()}/driver/messages?driverId=${encodeURIComponent(pageDriverId)}`
      : `${DRIVER_BASE()}/driver/messages`;
    const res = await fetch(url);
    if (!res.ok) return;
    const list = await res.json();
    if (!Array.isArray(list)) return;

    // pageDriverId already captured in request URL above

    for (const m of list) {
      try {
        // Accept either object or string
        let msg = m;
        if (typeof msg === "string") {
          // try parse
          try {
            msg = JSON.parse(msg);
          } catch (e) {
            // leave as string
          }
        }

        // If message looks like telemetry, skip it
        const t = (msg && msg.type) || "";
        if (
          String(t).toLowerCase().includes("telemetry") ||
          String(t).toLowerCase().includes("cp_telemetry")
        )
          continue;

        // Some Kafka messages are sent with a short prefix like: "sts: {\"cpUid\":\"1000\",\"driverId\":\"9001\",\"type\":\"startCharging\"}"
        // Detect simple prefixes (e.g. sts:, sts= or similar) and try to extract JSON payload
        if (typeof m === "string") {
          const s = m.trim();
          // match prefix like `word:` or `word=` followed by JSON
          const prefMatch = s.match(/^[a-zA-Z0-9_\-]+\s*[:=]\s*(\{[\s\S]*\})$/);
          if (prefMatch && prefMatch[1]) {
            try {
              const parsed = JSON.parse(prefMatch[1]);
              msg = parsed;
            } catch (e) {
              // if parse fails, leave as original string
            }
          }
        }

        // If message contains driverId and doesn't match our driver, skip
        if (msg && (msg.driverId != null || msg.driverid != null)) {
          const mid = String(msg.driverId ?? msg.driverid ?? "");
          if (pageDriverId && mid !== pageDriverId) continue;
        }

        // Convert message to pretty string (pretty-print JSON where possible)
        let pretty = "";
        try {
          if (typeof m === "string") {
            pretty = m;
            // if looks like JSON, pretty print
            try {
              const pm = JSON.parse(m);
              pretty = JSON.stringify(pm, null, 2);
            } catch (e) {
              // not JSON, keep as-is
            }
          } else if (m && typeof m === "object") {
            pretty = JSON.stringify(m, null, 2);
          } else {
            pretty = String(m);
          }
        } catch (e) {
          pretty = String(m || "(unprintable message)");
        }

        // Trim whitespace-only messages and show placeholder if empty
        if (!pretty || !String(pretty).trim()) pretty = "(empty message)";

        // Check if we have already displayed this message
        if (displayedMessages.has(pretty)) continue;
        // Mark message as displayed
        displayedMessages.add(pretty);

        if (!isHealthCheckMessage(pretty)) appendDriverMessage(pretty);
      } catch (e) {
        // ignore per-message failures
      }
    }
  } catch (e) {
    // ignore polling errors
  }
}

// Start polling messages on an interval similar to CP UI
let driverMsgInterval = null;
if (!driverMsgInterval)
  driverMsgInterval = setInterval(pollDriverMessages, 2000);

// Wire clear button if present
document.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("btnClear");
  if (btn)
    btn.addEventListener("click", () => {
      const c = document.getElementById("messages");
      if (c) c.textContent = "No messages yet.";
    });
  // wire driver API set button
  const setBtn = document.getElementById("btnSetDriverApi");
  if (setBtn) {
    setBtn.addEventListener("click", () => {
      const v = document.getElementById("driverApi")?.value;
      const norm = setDriverApi(v);
      appendDriverMessage(`Driver API set to ${norm}`);
    });
  }
  // ensure input shows current window.DRIVER_API or default
  try {
    const input = document.getElementById("driverApi");
    if (input) input.value = window.DRIVER_API || input.value || "";
  } catch (e) {}
});

// Load CPs
async function loadCPs() {
  const res = await fetch(API.cps);
  const data = await res.json();
  // Only show charging points that are activated
  const active = Array.isArray(data)
    ? data.filter((c) => (c.state || "").toLowerCase() === "activated")
    : [];
  if (tbody) {
    tbody.innerHTML = active
      .map(
        (c) => `
      <tr>
        <td>${c.uid}</td>
        <td>${c.name || ""}</td>
        <td>${c.location || ""}</td>
        <td>${(c.pricePerKWh ?? 0).toFixed(2)}</td>
        <td>${pill(c.state || "")}</td>
        <td>${fmt(c.lastHealthCheck)}</td>
      </tr>`
      )
      .join("");
  }
  const sel = $("#cpSelect");
  if (sel) {
    sel.innerHTML = active
      .map(
        (c) =>
          `<option value="${c.uid}">${c.uid} — ${
            c.name || c.location || ""
          }</option>`
      )
      .join("");
  }
}

// Add helper for single authoritative cpUid
window.currentCpUid = null;
function getCurrentCpUid() {
  // prefer stored cpUid, fall back to select value
  return (
    window.currentCpUid ||
    (document.querySelector("#cpSelect") &&
      document.querySelector("#cpSelect").value) ||
    null
  );
}

async function sendRequest() {
  const driverId = Number($("#driverId").value || 0);
  // cpUid may be a string UID; don't coerce to Number (it can become NaN)
  const cpUid =
    (document.querySelector("#cpSelect") &&
      document.querySelector("#cpSelect").value) ||
    "";
  if (!driverId || !cpUid) {
    alert("Set driverId and choose a CP");
    return;
  }
  const res = await fetch(API.req(cpUid, driverId), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ driverId, cpUid }),
  });
  const data = await res.json();
  // store the active cpUid when a request/session is created
  if (data && (data.requestId || data.sessionId)) {
    window.currentCpUid = String(cpUid);
    const sel = document.querySelector("#cpSelect");
    if (sel) sel.disabled = true; // prevent changing while active
    // Ensure we have an SSE subscription for the CP we just requested
    try {
      ensureTelemetrySse(String(cpUid));
    } catch (e) {}
  }
  if ($("#requestId")) $("#requestId").value = data.requestId || "";
  if ($("#reqOut")) $("#reqOut").textContent = jfmt(data);
}

async function checkRequest() {
  const id = $("#requestId").value.trim();
  if (!id) {
    alert("Enter requestId");
    return;
  }
  const res = await fetch(API.reqStatus(id));
  const data = await res.json();
  if ($("#reqOut")) $("#reqOut").textContent = jfmt(data);
  if (data.sessionId && $("#sessionId")) $("#sessionId").value = data.sessionId;
}

async function stopSession() {
  // use the single authoritative cpUid
  const cpUid = getCurrentCpUid();
  // Prefer stopping by CP UID (the central endpoint expects cpUid in the path).
  // If no CP is selected, fall back to using a sessionId input (existing behavior).
  const sessionInput = document.querySelector("#sessionId");
  const sessionId = sessionInput ? sessionInput.value.trim() : "";

  let url;
  if (cpUid) {
    // Send stop command to the DRIVER API (driver service) so the device controller
    // can act on the stop immediately: /driver/sessions/{cpUid}/stop
    url = `${DRIVER_BASE()}/driver/sessions/${cpUid}/stop`;
  } else if (sessionId) {
    // fallback to driver-local endpoint using sessionId on the driver API host
    url = `${DRIVER_BASE()}/driver/sessions/${sessionId}/stop`;
  } else {
    alert("Select a CP or enter a sessionId");
    return;
  }

  const res = await fetch(url, { method: "POST" });
  let data;
  try {
    data = await res.json();
  } catch (err) {
    data = { status: res.status, ok: res.ok, message: "No JSON response" };
  }
  if ($("#sessOut")) $("#sessOut").textContent = jfmt(data);
  // after successful stop, clear stored cpUid and re-enable select
  try {
    // existing fetch...
  } finally {
    window.currentCpUid = null;
    const sel = document.querySelector("#cpSelect");
    if (sel) sel.disabled = false;
  }
}

async function getTicket() {
  const id = $("#ticketSessionId").value.trim();
  if (!id) {
    alert("Enter sessionId");
    return;
  }
  const res = await fetch(API.ticket(id));
  const data = await res.json();
  if ($("#ticketOut")) $("#ticketOut").textContent = jfmt(data);
}

async function startSim() {
  const filePath = $("#simFile").value.trim();
  const driverId = Number($("#driverId").value || 0);
  if (!filePath || !driverId) {
    alert("Set file path and driverId");
    return;
  }
  const res = await fetch(API.simStart, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ filePath, driverId }),
  });
  const data = await res.json();
  if ($("#simOut")) $("#simOut").textContent = jfmt(data);
}

async function stopSim() {
  const res = await fetch(API.simStop, { method: "POST" });
  const data = await res.json();
  if ($("#simOut")) $("#simOut").textContent = jfmt(data);
}

// Bind events (if elements exist on the page)
$("#btnLoadCps") && ($("#btnLoadCps").onclick = loadCPs);
$("#btnRequest") && ($("#btnRequest").onclick = sendRequest);
// When the CP selection changes, open a telemetry SSE for the newly selected CP
const cpSelectEl = document.querySelector("#cpSelect");
if (cpSelectEl) {
  cpSelectEl.addEventListener("change", (ev) => {
    const v = ev.target.value;
    if (v) ensureTelemetrySse(v);
  });
}
$("#btnCheckReq") && ($("#btnCheckReq").onclick = checkRequest);
$("#btnGetSession") && ($("#btnGetSession").onclick = getSession);
$("#btnStopSession") && ($("#btnStopSession").onclick = stopSession);
$("#btnGetTicket") && ($("#btnGetTicket").onclick = getTicket);
$("#btnStartSim") && ($("#btnStartSim").onclick = startSim);
$("#btnStopSim") && ($("#btnStopSim").onclick = stopSim);

// Init
loadCPs();

// --- Telemetry handling (SSE from central) ---------------------------------
// We will open EventSource streams per CP UID when listed. The panel below
// will only update when the telemetry payload's driverId matches the Driver ID
// input on the page (so the driver only sees sessions it is involved with).

const telemetrySseMap = new Map();

function updateTelemetryPanel(data, cpUid) {
  const setText = (id, value) => {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
  };
  // If data is null or falsy, clear/reset the panel
  if (!data) {
    setText("t-cp", cpUid || "-");
    setText("t-session", "-");
    setText("t-driver", "-");
    setText("t-energy", "-");
    setText("t-power", "-");
    setText("t-cost", "-");
    setText("t-ts", "-");
    return;
  }
  setText("t-cp", cpUid || "-");
  setText("t-session", data.sessionId || "-");
  setText("t-driver", data.driverId != null ? String(data.driverId) : "-");
  setText(
    "t-energy",
    data.energy_kWh != null ? Number(data.energy_kWh).toFixed(3) : "-"
  );
  setText(
    "t-power",
    data.power_kW != null ? Number(data.power_kW).toFixed(2) : "-"
  );
  setText(
    "t-cost",
    data.cost_eur != null ? Number(data.cost_eur).toFixed(4) : "-"
  );
  setText(
    "t-ts",
    data.timestamp ? new Date(data.timestamp).toLocaleString() : "-"
  );
}

// Open SSE for given cpUid (if not already open). Uses Central's telemetry stream.
function ensureTelemetrySse(cpUid) {
  if (!cpUid) return;
  if (telemetrySseMap.has(cpUid)) return;
  try {
    const url = `${CENTRAL_BASE}/central/telemetry/stream/${cpUid}`;
    console.debug("driver: opening telemetry SSE for", cpUid, "->", url);
    const src = new EventSource(url);
    src.addEventListener("telemetry", (ev) => {
      try {
        const data = JSON.parse(ev.data);
        console.debug("driver: telemetry event for", cpUid, data);
        // Only show telemetry for this driver id
        const pageDriverId = Number(
          document.getElementById("driverId")?.value || 0
        );
        // Only update if driverId matches and if this driver has selected or active cpUid
        const matchesDriver =
          data.driverId != null && Number(data.driverId) === pageDriverId;
        const currentCp =
          window.currentCpUid ||
          (document.querySelector("#cpSelect") &&
            document.querySelector("#cpSelect").value);
        const matchesCp = String(currentCp) === String(cpUid);
        if (matchesDriver && matchesCp) {
          updateTelemetryPanel(data, cpUid);
        }
      } catch (e) {
        console.error("Failed to parse telemetry SSE", e);
      }
    });
    // Fallback: some servers send unnamed 'message' events
    src.onmessage = (ev) => {
      try {
        console.debug("driver: message event for", cpUid, ev.data);
        const data = JSON.parse(ev.data);
        const pageDriverId = Number(
          document.getElementById("driverId")?.value || 0
        );
        const matchesDriver =
          data.driverId != null && Number(data.driverId) === pageDriverId;
        const currentCp =
          window.currentCpUid ||
          (document.querySelector("#cpSelect") &&
            document.querySelector("#cpSelect").value);
        const matchesCp = String(currentCp) === String(cpUid);
        if (matchesDriver && matchesCp) updateTelemetryPanel(data, cpUid);
      } catch (e) {
        // ignore parse errors
      }
    };
    src.onerror = (err) => {
      console.warn("Telemetry SSE error for", cpUid, err);
    };
    telemetrySseMap.set(cpUid, src);
  } catch (e) {
    console.error("Failed to create telemetry SSE for", cpUid, e);
  }
}

// Whenever CP list is loaded, ensure SSEs exist for those CPs (only activated ones)
function ensureSsesForActive(list) {
  if (!Array.isArray(list)) return;
  list.forEach((c) => {
    if (c && c.uid) ensureTelemetrySse(c.uid);
  });
}

// Hook into loadCPs by wrapping it: after loading CPs we will call ensureSsesForActive
const originalLoadCPs = loadCPs;
loadCPs = async function () {
  const res = await originalLoadCPs();
  try {
    // fetch the cps list directly so we can open SSEs (original loadCPs already fetched once,
    // but to avoid changing it we fetch again here).
    const r = await fetch(API.cps);
    if (r.ok) {
      const data = await r.json();
      const active = Array.isArray(data)
        ? data.filter((c) => (c.state || "").toLowerCase() === "activated")
        : [];
      ensureSsesForActive(active);
      return data;
    }
  } catch (e) {
    // ignore
  }
};

// Make sure we open SSEs at least once for the active CPs on page load.
// The original code called loadCPs() before we wrapped it; call it again
// so the wrapped version can set up SSEs.
try {
  loadCPs().catch((e) => console.warn("loadCPs (wrapped) failed:", e));
} catch (e) {
  console.warn("loadCPs invocation failed:", e);
}

// When stopping a session, also clear current telemetry panel and close SSE for that cpUid
const originalStopSession = stopSession;
stopSession = async function () {
  const cpUid = getCurrentCpUid();
  await originalStopSession();
  // clear UI
  updateTelemetryPanel(null, "-");
  if (cpUid && telemetrySseMap.has(cpUid)) {
    try {
      telemetrySseMap.get(cpUid).close();
    } catch (e) {}
    telemetrySseMap.delete(cpUid);
  }
};
