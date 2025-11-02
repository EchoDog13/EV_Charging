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
$("#btnCheckReq") && ($("#btnCheckReq").onclick = checkRequest);
$("#btnGetSession") && ($("#btnGetSession").onclick = getSession);
$("#btnStopSession") && ($("#btnStopSession").onclick = stopSession);
$("#btnGetTicket") && ($("#btnGetTicket").onclick = getTicket);
$("#btnStartSim") && ($("#btnStartSim").onclick = startSim);
$("#btnStopSim") && ($("#btnStopSim").onclick = stopSim);

// Init
loadCPs();
