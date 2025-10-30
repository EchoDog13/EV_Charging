// Use the same origin that served the page by default so the dashboard works when
// loaded from the central server. Fallback to the previous hard-coded host if
// window.location is not available (rare in some embedding scenarios).
const API_BASE =
  typeof window !== "undefined" && window.location && window.location.origin
    ? window.location.origin
    : "http://192.168.100.100:9900";

const tbody = document.querySelector("#tbl tbody");

function pill(state) {
  return `<span class="pill ${state}">${state}</span>`;
}
function fmt(ts) {
  return ts ? new Date(ts).toLocaleString() : "—";
}

async function load() {
  const res = await fetch(API_BASE + "/central/cps");
  if (!res.ok) throw new Error("Fetch failed: " + res.status);
  const data = await res.json();
  render(data);
}

function render(list) {
  // Sort by requested state priority: charging, paused, activated, stopped, disconnected
  const priority = {
    SUPPLYING: 0, // corresponds to 'charging'
    PAUSED: 1, // not present in current enum but keep for future
    ACTIVATED: 2,
    STOPPED: 3,
    DISCONNECTED: 4,
    OUT_OF_ORDER: 5,
  };
  const sorted = [...list].sort((a, b) => {
    const sa = (a.state || "").toUpperCase();
    const sb = (b.state || "").toUpperCase();
    const pa = Object.prototype.hasOwnProperty.call(priority, sa)
      ? priority[sa]
      : 99;
    const pb = Object.prototype.hasOwnProperty.call(priority, sb)
      ? priority[sb]
      : 99;
    if (pa !== pb) return pa - pb;
    // Tie-breaker: by uid if same priority
    return (a.uid ?? 0) - (b.uid ?? 0);
  });

  tbody.innerHTML = sorted
    .map(
      (ch) => `
    <tr data-uid="${ch.uid}">
      <td>${ch.uid}</td>
      <td>${ch.location || ""}</td>
      <td>${(ch.pricePerKWh ?? 0).toFixed(2)}</td>
      <td class="state">${pill(ch.state)}</td>
      <td class="last">${fmt(ch.lastHealthCheck)}</td>
      <td><button class="stop-btn" data-uid="${ch.uid}">Stop</button></td>
    </tr>`
    )
    .join("");

  // After rendering, ensure SSE connections exist for visible CPs
  sorted.forEach((ch) => {
    const uid = ch.uid;
    ensureSse(uid);
  });

  // wire click handlers to rows to select a CP for telemetry
  tbody.querySelectorAll("tr[data-uid]").forEach((tr) => {
    tr.addEventListener("click", () => {
      const uid = tr.getAttribute("data-uid");
      selectCp(uid);
      // highlight selection
      tbody
        .querySelectorAll("tr")
        .forEach((r) => r.classList.remove("selected"));
      tr.classList.add("selected");
    });
  });

  // wire stop button handlers
  tbody.querySelectorAll("button.stop-btn").forEach((btn) => {
    btn.addEventListener("click", async (ev) => {
      ev.stopPropagation(); // prevent row click selection
      const uid = btn.getAttribute("data-uid");
      const driverId = prompt("Driver ID (optional):");
      try {
        const params = driverId
          ? `?driverId=${encodeURIComponent(driverId)}`
          : "";
        const res = await fetch(
          `${API_BASE}/central/cps/${uid}/stop${params}`,
          {
            method: "POST",
          }
        );
        if (!res.ok) throw new Error(`Stop failed: ${res.status}`);
        alert(`Stop command sent for charger ${uid}`);
        // optimistic UI: mark state as STOPPED
        const row = document.querySelector(`tr[data-uid='${uid}']`);
        if (row) {
          const stateCell = row.querySelector(".state");
          if (stateCell) stateCell.innerHTML = pill("STOPPED");
        }
      } catch (e) {
        console.error(e);
        alert("Failed to send stop command");
      }
    });
  });
}

// Poll every 5 seconds
load().catch(console.error);
// Keep polling the list (for new/removed CPs) every 10s, but telemetry will arrive via SSE
setInterval(() => load().catch(console.error), 10000);

// Button handlers for start/stop all
document.addEventListener("DOMContentLoaded", () => {
  const startBtn = document.getElementById("start-all");
  const stopBtn = document.getElementById("stop-all");

  startBtn?.addEventListener("click", async () => {
    try {
      const res = await fetch(API_BASE + "/central/cps/commands/start-all", {
        method: "POST",
      });
      if (!res.ok) throw new Error("Start all failed: " + res.status);
      alert("Start all command sent");
    } catch (e) {
      console.error(e);
      alert("Failed to send start-all");
    }
  });

  stopBtn?.addEventListener("click", async () => {
    try {
      const res = await fetch(API_BASE + "/central/cps/commands/stop-all", {
        method: "POST",
      });
      if (!res.ok) throw new Error("Stop all failed: " + res.status);
      alert("Stop all command sent");
    } catch (e) {
      console.error(e);
      alert("Failed to send stop-all");
    }
  });
});

fetch("http://192.168.100.100:9900/central/cps")
  .then((r) => r.json())
  .then((list) => {
    const priority = {
      charging: 0,
      paused: 1,
      activated: 2,
      stopped: 3,
      disconnected: 4,
    };
    const sorted = [...list].sort((a, b) => {
      const sa = (a.state || "").toLowerCase(),
        sb = (b.state || "").toLowerCase();
      const pa = Object.prototype.hasOwnProperty.call(priority, sa)
        ? priority[sa]
        : 99;
      const pb = Object.prototype.hasOwnProperty.call(priority, sb)
        ? priority[sb]
        : 99;
      return pa !== pb ? pa - pb : (a.uid || 0) - (b.uid || 0);
    });
    console.table(sorted.map((s) => ({ uid: s.uid, state: s.state })));
  })
  .catch(console.error);

// SSE handling: connect to telemetry stream per cpUid and update rows
const sseMap = new Map();

// currently selected CP for telemetry panel
let selectedCp = null;

function selectCp(uid) {
  selectedCp = uid;
  const panel = document.getElementById("telemetry-panel");
  const fields = document.getElementById("telemetry-fields");
  const sel = document.getElementById("telemetry-selected");
  if (panel) panel.style.display = "block";
  if (sel) sel.style.display = "none";
  if (fields) fields.style.display = "block";
  const tcp = document.getElementById("t-cp");
  if (tcp) tcp.textContent = uid;
}

function updateTelemetryPanel(data) {
  if (!data) return;
  const setText = (id, value) => {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
  };
  setText("t-session", data.sessionId || "-");
  setText("t-driver", data.driverId || "-");
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

function ensureSse(uid) {
  if (!uid) return;
  if (sseMap.has(uid)) return;
  try {
    const src = new EventSource(`${API_BASE}/central/telemetry/stream/${uid}`);
    src.addEventListener("telemetry", (ev) => {
      try {
        const data = JSON.parse(ev.data);
        // Update row for this uid
        const row = document.querySelector(`tr[data-uid='${uid}']`);
        if (row) {
          const stateCell = row.querySelector(".state");
          const lastCell = row.querySelector(".last");
          // map incoming status/state keys if present; if telemetry has no explicit
          // state, treat telemetry as an indicator the CP is SUPPLYING (charging)
          let incomingState = null;
          if (data.state) incomingState = data.state;
          else if (data.sessionId || data.energy_kWh || data.power_kW) incomingState = "SUPPLYING";
          if (incomingState) stateCell.innerHTML = pill(String(incomingState).toUpperCase());
          if (data.timestamp)
            lastCell.textContent = new Date(data.timestamp).toLocaleString();
        }
        // If selected, update telemetry panel
        if (String(selectedCp) === String(uid)) {
          updateTelemetryPanel(data);
        }
        console.log("Telemetry (SSE)", uid, data);
      } catch (e) {
        console.error("Failed to parse SSE telemetry", e);
      }
    });
    src.onerror = (err) => {
      console.warn("SSE connection error for", uid, err);
      // leave to browser to attempt reconnection
    };
    sseMap.set(uid, src);
  } catch (e) {
    console.error("Failed to create SSE for", uid, e);
  }
}
