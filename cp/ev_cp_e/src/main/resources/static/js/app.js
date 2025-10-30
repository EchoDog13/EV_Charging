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
let statePollTimer = null;
let statePollRunning = false;
const maxMessages = 50;
let lastStateSeen = null;

async function load() {
  try {
    const r = await fetch(`/cp/${cpUid()}/state`);
    const j = await r.json();
    q("#output").textContent = JSON.stringify(j, null, 2);
    updateStatusBadge(j.state);
    // Ensure the background state poller is running after an explicit load
    startStatePolling();
  } catch (e) {
    q("#output").textContent = "Error loading: " + e.message;
  }
}

function stopStatePolling() {
  if (statePollTimer) {
    clearTimeout(statePollTimer);
    statePollTimer = null;
  }
  statePollRunning = false;
}

function startStatePolling() {
  if (statePollRunning) return;
  statePollRunning = true;

  // Recursive timeout so we can vary the interval depending on state
  (async function schedule() {
    if (!statePollRunning) return;
    try {
      const r = await fetch(`/cp/${encodeURIComponent(cpUid())}/state`);
      if (r.ok) {
        const j = await r.json();
        // Update output and badge only if there's a change or if supplying
        const currentState = j.state || "";
        q("#output").textContent = JSON.stringify(j, null, 2);
        updateStatusBadge(currentState);
        lastStateSeen = currentState || lastStateSeen;

        // while supplying, poll more aggressively
        const delay =
          currentState && currentState.toLowerCase() === "supplying"
            ? 1000
            : 5000;
        statePollTimer = setTimeout(schedule, delay);
        return;
      }
    } catch (e) {
      // ignore transient errors but keep polling at a slow rate
    }
    // fallback delay on error
    statePollTimer = setTimeout(schedule, 5000);
  })();
}

function updateStatusBadge(state) {
  const el = q("#statusBadge");
  if (!el) return;
  const s = (state || "unknown").toLowerCase();
  let cls = s.replace(/\s+/g, "_");
  el.className = "status " + cls;

  try {
    if (s === "supplying") {
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
        el.textContent = `Supplying — ${energy} ${cost} ${driver}`.trim();
        return;
      }
    }
  } catch (e) {
    // ignore
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
  while (container.children.length > maxMessages)
    container.removeChild(container.lastChild);
}

async function pollMessages() {
  try {
    const r = await fetch(`/api/cp/${encodeURIComponent(cpUid())}/messages`);
    if (r.status === 200) {
      const arr = await r.json();
      if (Array.isArray(arr)) {
        const container = q("#messages");
        if (!container) return;
        container.textContent = "";
        arr.forEach((m) => {
          const text = typeof m === "string" ? m : JSON.stringify(m);
          const el = document.createElement("div");
          el.className = "message";
          el.textContent = new Date().toLocaleTimeString() + " — " + text;
          container.appendChild(el);
        });
        while (container.children.length > maxMessages)
          container.removeChild(container.lastChild);

        // Refresh state badge
        try {
          const rs = await fetch(`/cp/${encodeURIComponent(cpUid())}/state`);
          if (rs.ok) {
            const js = await rs.json();
            updateStatusBadge(js.state);
            lastStateSeen = js.state || lastStateSeen;
          }
        } catch (e) {}
        return;
      }
    }
  } catch (e) {}

  // Fallback: poll state and show change
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
  } catch (e) {}
}

async function startSession() {
  const driver = q("#driverId") ? q("#driverId").value.trim() : "10";
  const btn = q("#btnRequestCharge");
  if (btn) btn.disabled = true;
  try {
    const res = await fetch(
      `/cp/${cpUid()}/charge-requests?driverId=${encodeURIComponent(driver)}`,
      { method: "POST" }
    );
    const data = await res.json().catch(() => ({}));
    q("#output").textContent =
      "Session started:\n" + JSON.stringify(data, null, 2);
    appendMessage("Charge request sent");
    await load();
    await pollMessages();
  } catch (e) {
    q("#output").textContent = "Charge request failed: " + e.message;
    appendMessage("Charge request error: " + e.message);
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function togglePlug() {
  try {
    const r = await fetch(`/cp/${cpUid()}/state`);
    if (!r.ok) {
      appendMessage("Failed to read state");
      return;
    }
    const j = await r.json();
    const current = j.state || "";
    if (current === "supplying") {
      const ru = await fetch(`/cp/${cpUid()}/unplug`, { method: "POST" });
      const txt = await ru.text();
      appendMessage("Action: unplug -> " + txt);
      // Unplug now ends the session: stop state polling so UI doesn't keep showing supplying
      stopStatePolling();
    } else {
      const rp = await fetch(`/cp/${cpUid()}/plug`, { method: "POST" });
      const txt = await rp.text();
      appendMessage("Action: plug -> " + txt);
      // After plugging, ensure polling resumes so the UI picks up supplying state
      startStatePolling();
    }
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

// Stop button removed from UI; related control actions are now handled via central or plug/unplug flows.

// === BUTTON EVENT BINDINGS ===
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

if (q("#btnRequestCharge")) {
  q("#btnRequestCharge").onclick = startSession;
}

// Attach stop handler when DOM is ready
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", attachStopHandler);
} else {
  attachStopHandler();
}

// Initial load
load();

// Start polling every 2 seconds
if (!msgInterval) {
  msgInterval = setInterval(pollMessages, 2000);
}

// === TELEMETRY SIMULATION (unchanged) ===
function sendTelemetry() {
  const energy = (Math.random() * 50).toFixed(3);
  const power = (Math.random() * 22).toFixed(1);
  fetch(`/cp/${cpUid()}/telemetry?kWh=${energy}&power=${power}`, {
    method: "POST",
  }).catch((e) => console.error("Telemetry failed:", e));
}
