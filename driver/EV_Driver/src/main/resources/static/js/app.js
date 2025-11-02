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

const DRIVER_API_BASE = "100.83.66.30:7040"; // driver API host:port (may omit protocol)
// Ensure DRIVER_API_BASE contains protocol for fetch; default to http:// when absent
const driverHasProto = /^https?:\/\//i.test(DRIVER_API_BASE);
const DRIVER_BASE = driverHasProto
  ? DRIVER_API_BASE
  : `http://${DRIVER_API_BASE}`;

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
    url = `${DRIVER_BASE}/driver/sessions/${cpUid}/stop`;
  } else if (sessionId) {
    // fallback to driver-local endpoint using sessionId on the driver API host
    url = `${DRIVER_BASE}/driver/sessions/${sessionId}/stop`;
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
