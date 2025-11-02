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

const API = {
  // Central's CPS endpoint (absolute URL) — used by the front-end to list available CPs
  cps: `${CENTRAL_BASE}/central/cps`,
  req: "/driver/charge-requests/${cpUid}/driver/${driverId}",
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

async function sendRequest() {
  const driverId = Number($("#driverId").value || 0);
  const cpUid = Number($("#cpSelect").value || 0);
  if (!driverId || !cpUid) {
    alert("Set driverId and choose a CP");
    return;
  }
  const res = await fetch(API.req, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ driverId, cpUid }),
  });
  const data = await res.json();
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

async function getSession() {
  const id = $("#sessionId").value.trim();
  if (!id) {
    alert("Enter sessionId");
    return;
  }
  const res = await fetch(API.session(id));
  const data = await res.json();
  if ($("#sessOut")) $("#sessOut").textContent = jfmt(data);
}

async function stopSession() {
  const id = $("#sessionId").value.trim();
  if (!id) {
    alert("Enter sessionId");
    return;
  }
  const res = await fetch(API.stop(id), { method: "POST" });
  const data = await res.json();
  if ($("#sessOut")) $("#sessOut").textContent = jfmt(data);
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
