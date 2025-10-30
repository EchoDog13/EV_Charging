const q = (s) => document.querySelector(s);
// Use same origin for central calls when possible
const CENTRAL_BASE =
  typeof window !== "undefined" && window.location && window.location.origin
    ? window.location.origin
    : "http://localhost:9900";
// Diagnostic startup log
console.log(
  "CP app starting. CENTRAL_BASE=",
  CENTRAL_BASE,
  "location=",
  window.location.href
);

// getCentralBase removed; use CENTRAL_BASE (page origin) by default

// Surface global JS errors into the messages panel to aid debugging
window.addEventListener("error", (ev) => {
  try {
    appendMessage(`JS error: ${ev.message} at ${ev.filename}:${ev.lineno}`);
  } catch (e) {
    console.error("Failed to append JS error", e);
  }
});
window.addEventListener("unhandledrejection", (ev) => {
  try {
    appendMessage(
      "Unhandled rejection: " +
        (ev.reason && ev.reason.message ? ev.reason.message : String(ev.reason))
    );
  } catch (e) {
    console.error("Failed to append rejection", e);
  }
});
const cpUid = () => q("#cpUid").value.trim();
let simInterval = null;
let msgInterval = null;
const maxMessages = 50;
let lastStateSeen = null;

async function load() {
  try {
    const r = await fetch(`/cp/${cpUid()}/state`);
    const j = await r.json();
    q("#output").textContent = JSON.stringify(j, null, 2);
    updateStatusBadge(j.state);
  } catch (e) {
    q("#output").textContent = "Error loading: " + e.message;
  }
}

function updateStatusBadge(state) {
  const el = q("#statusBadge");
  if (!el) return;
  const s = (state || "unknown").toLowerCase();
  // Normalize spaces and special names
  let cls = s.replace(/\s+/g, "_");
  el.className = "status " + cls;
  // If the state object contains more details (when load() added them),
  // display an informative label for supplying
  try {
    if (s === "supplying") {
      // attempt to fetch more details from the last loaded output
      const out = q("#output").textContent;
      if (out && out !== "—") {
        const j = JSON.parse(out);
        const energy =
          j.energy_kWh !== undefined
            ? Number(j.energy_kWh).toFixed(3) + " kWh"
            : "";
        const cost =
          j.cost_eur !== undefined ? "€" + Number(j.cost_eur).toFixed(2) : "";
        const driver = j.driverId ? "Driver: " + j.driverId : "";
        el.textContent = `Supplying — ${energy} ${cost} ${driver}`;
        return;
      }
    }
  } catch (e) {
    // fallback to simple label
  }
  el.textContent = state ? state : "—";
}

function appendMessage(text) {
  const container = q("#messages");
  if (!container) return;
  if (container.textContent === "No messages yet.") container.textContent = "";
  const el = document.createElement("div");
  el.className = "message";
  el.textContent = new Date().toLocaleTimeString() + " — " + text;
  container.prepend(el);
  // Trim messages
  while (container.children.length > maxMessages)
    container.removeChild(container.lastChild);
}

async function pollMessages() {
  // First try a messages endpoint; fall back to polling state if missing
  try {
    const r = await fetch(`/api/cp/${encodeURIComponent(cpUid())}/messages`);
    if (r.status === 200) {
      const arr = await r.json();
      if (Array.isArray(arr)) {
        // Render snapshot: replace current contents with the server-provided
        // messages to avoid duplicates across polls.
        const container = q("#messages");
        if (!container) return;
        container.textContent = "";
        // Server returns newest-first; render in that order so latest appears on top
        arr.forEach((m) => {
          const text = typeof m === "string" ? m : JSON.stringify(m);
          const el = document.createElement("div");
          el.className = "message";
          el.textContent = new Date().toLocaleTimeString() + " — " + text;
          container.appendChild(el);
        });
        // Trim to maxMessages if server returned more
        while (container.children.length > maxMessages)
          container.removeChild(container.lastChild);
        // Also refresh the CP state so the status badge stays up-to-date
        try {
          const rs = await fetch(`/cp/${encodeURIComponent(cpUid())}/state`);
          if (rs.ok) {
            const js = await rs.json();
            updateStatusBadge(js.state);
            lastStateSeen = js.state || lastStateSeen;
          }
        } catch (e) {
          // ignore state refresh errors
        }
        return;
      }
    }
  } catch (e) {
    // ignore and try fallback
  }

  // Fallback: poll state and show a short note when state changes
  try {
    const r2 = await fetch(`/cp/${encodeURIComponent(cpUid())}/state`);
    if (r2.ok) {
      const j = await r2.json();
      const newState = j.state || JSON.stringify(j);
      if (newState !== lastStateSeen) {
        appendMessage("State: " + newState);
        lastStateSeen = newState;
        updateStatusBadge(newState);
      }
    }
  } catch (e) {
    // noop
  }
}

async function startSession() {
  // create a manual charge request for this CP via central/authorize-like endpoint
  const driver = q("#driverId") ? q("#driverId").value.trim() : "10";
  const res = await fetch(
    `/cp/${cpUid()}/charge-requests?driverId=${encodeURIComponent(driver)}`,
    {
      method: "POST",
    }
  );
  const data = await res.json().catch(() => ({}));
  q("#output").textContent =
    "Session started:\n" + JSON.stringify(data, null, 2);
}

async function stopSession() {
  const driver = q("#driverId") ? q("#driverId").value.trim() : "10";
  const btn = q("#btnRequestCharge");
  if (btn) btn.disabled = true;
  try {
    // create a manual charge request for this CP via central/authorize-like endpoint
    const res = await fetch(
      `/cp/${cpUid()}/charge-requests?driverId=${encodeURIComponent(driver)}`,
      {
        method: "POST",
      }
    );
    const text = await res.text();
    q("#output").textContent = "Charge request response:\n" + text;
    appendMessage("OUT CP->central request: " + text);
    // refresh state and messages to reflect any immediate changes
    await load();
    await pollMessages();
  } catch (e) {
    q("#output").textContent = "Charge request failed: " + e.message;
    appendMessage("Charge request error: " + e.message);
  } finally {
    if (btn) btn.disabled = false;
  }
  await fetch(`/cp/${cpUid()}/telemetry?kWh=${energy}&power=${power}`, {
    method: "POST",
  });
  q("#output").textContent = "Telemetry sent.";
}

async function togglePlug() {
  try {
    // Read current state
    const r = await fetch(`/cp/${cpUid()}/state`);
    if (!r.ok) {
      appendMessage("Failed to read state");
      return;
    }
    const j = await r.json();
    const current = j.state || "";
    if (current === "supplying") {
      // currently plugged/supplying -> unplug
      const ru = await fetch(`/cp/${cpUid()}/unplug`, { method: "POST" });
      const txt = await ru.text();
      appendMessage("Action: unplug -> " + txt);
    } else {
      // PAUSED or any other non-supplying state -> attempt to plug (resume/start)
      const rp = await fetch(`/cp/${cpUid()}/plug`, { method: "POST" });
      const txt = await rp.text();
      appendMessage("Action: plug -> " + txt);
    }
    // refresh state and messages immediately
    await load();
    await pollMessages();
  } catch (e) {
    appendMessage("togglePlug error: " + e.message);
  }
}

function simulate() {
  if (simInterval) return;
  q("#output").textContent = "Simulation running...";
  simInterval = setInterval(sendTelemetry, 4000);
}

function stopSim() {
  if (simInterval) {
    clearInterval(simInterval);
    simInterval = null;
    q("#output").textContent = "Simulation stopped.";
  }
}

q("#btnLoad").onclick = () => {
  lastStateSeen = null;
  const c = q("#messages");
  if (c) c.textContent = "No messages yet.";
  load();
};
q("#btnClear").onclick = () => {
  lastStateSeen = null;
  const c = q("#messages");
  if (c) c.textContent = "No messages yet.";
};
// Attach request charge button
if (q("#btnRequestCharge")) q("#btnRequestCharge").onclick = startSession;

// Stop charging button (publish stopCharging to central)
function attachStopHandler() {
  const btn = q("#btnStopCharge");
  if (!btn) return;
  if (btn.__stopBound) return;
  btn.__stopBound = true;
  btn.addEventListener("click", async () => {
    const driver = q("#driverId") ? q("#driverId").value.trim() : "";
    const uid = cpUid();
    appendMessage(`DEBUG: Stop button clicked for ${uid} (driver=${driver})`);
    // Quick ping to central test endpoint for visibility
    try {
      const pingUrl = `${CENTRAL_BASE}/central/test`;
      appendMessage("DEBUG: Pinging central -> " + pingUrl);
      console.log("Pinging central at", pingUrl);
      const ping = await fetch(pingUrl, { method: "GET", mode: "cors" });
      appendMessage("Ping status: " + ping.status);
    } catch (e) {
      appendMessage("Ping failed: " + e.message);
    }

    const payload = { type: "stopCharging", chargerId: uid };
    if (driver) payload.driverId = driver;
    try {
      const url = `${CENTRAL_BASE}/central/cps/${encodeURIComponent(uid)}/stop${
        driver ? `?driverId=${encodeURIComponent(driver)}` : ""
      }`;
      console.log("Sending stop to central:", url, "payload:", payload);
      appendMessage("DEBUG: Sending stop POST -> " + url);
      const res = await fetch(url, { method: "POST", mode: "cors" });
      const text = await res.text().catch(() => "");
      appendMessage("Stop command sent -> " + (text || res.status));
      q("#output").textContent =
        "Stop command sent. Response: " + (text || res.status);
    } catch (e) {
      appendMessage("Failed to send stop command: " + e.message);
      q("#output").textContent = "Stop failed: " + e.message;
      console.error("Stop POST error:", e);
    }
  });
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", attachStopHandler);
} else {
  attachStopHandler();
}
load();

// Start polling messages every 2s
if (!msgInterval) msgInterval = setInterval(pollMessages, 2000);
