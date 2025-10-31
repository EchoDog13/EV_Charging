const q = (s) => document.querySelector(s);

// Allow overriding CP API base (useful when running multiple charger instances)
function cpBuildBase() {
  try {
    const stored = localStorage.getItem("cp_api_base");
    if (stored) return stored;
  } catch (e) {}
  if (
    typeof window !== "undefined" &&
    window.location &&
    window.location.origin
  )
    return window.location.origin;
  return "http://localhost:9900";
}
let CP_BASE = cpBuildBase();
function cpSetBase(v) {
  CP_BASE = v;
  try {
    if (v == null) localStorage.removeItem("cp_api_base");
    else localStorage.setItem("cp_api_base", v);
  } catch (e) {}
  // Accept raw port (e.g. "9901") or full URL (http://host:port) and normalize
  function normalize(input) {
    if (!input && input !== "") return input;
    const s = String(input).trim();
    if (!s) return null;
    // port only
    if (/^\d+$/.test(s)) {
      try {
        if (typeof window !== "undefined" && window.location) {
          return `${window.location.protocol}//${window.location.hostname}:${s}`;
        }
      } catch (e) {}
      return `http://localhost:${s}`;
    }
    // leading :9901 => use same host
    if (/^:\d+$/.test(s)) {
      const p = s.replace(/^:/, "");
      try {
        if (typeof window !== "undefined" && window.location) {
          return `${window.location.protocol}//${window.location.hostname}:${p}`;
        }
      } catch (e) {}
      return `http://localhost:${p}`;
    }
    // if it already looks like http(s) URL, keep
    if (/^https?:\/\//i.test(s)) return s;
    // otherwise assume host:port or hostname only
    return s;
  }

  const normalized = normalize(v);
  CP_BASE = normalized || cpBuildBase();
  try {
    if (!normalized) localStorage.removeItem("cp_api_base");
    else localStorage.setItem("cp_api_base", normalized);
  } catch (e) {}
}
window.cpConfig = { getBase: () => CP_BASE, setBase: cpSetBase };

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
    const r = await fetch(`${CP_BASE}/cp/${cpUid()}/state`);
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
      const r = await fetch(
        `${CP_BASE}/cp/${encodeURIComponent(cpUid())}/state`
      );
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
  // Skip health-check messages that clutter the UI
  try {
    if (isHealthCheckMessage(text)) return;
  } catch (e) {
    // if detection fails, fall back to showing the message
  }

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

// Helper to detect health-check / actuator messages
function isHealthCheckMessage(text) {
  if (!text) return false;

  let s = String(text).trim();

  // Try to parse as JSON
  try {
    const obj = JSON.parse(s);
    if (obj.function && obj.function.toLowerCase() === "healthcheck") {
      return true;
    }
    if (obj.type && obj.type.toLowerCase() === "health") {
      return true;
    }
  } catch (e) {
    // Not JSON, fallback to simple text match
    if (/\/actuator\/health\b|actuator\/health\b|health check\b/i.test(s)) {
      return true;
    }
  }

  return false;
}

async function pollMessages() {
  try {
    const r = await fetch(
      `${CP_BASE}/api/cp/${encodeURIComponent(cpUid())}/messages`
    );
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
          const rs = await fetch(
            `${CP_BASE}/cp/${encodeURIComponent(cpUid())}/state`
          );
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
    const r2 = await fetch(
      `${CP_BASE}/cp/${encodeURIComponent(cpUid())}/state`
    );
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
      `${CP_BASE}/cp/${cpUid()}/charge-requests?driverId=${encodeURIComponent(
        driver
      )}`,
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
    const r = await fetch(`${CP_BASE}/cp/${cpUid()}/state`);
    if (!r.ok) {
      appendMessage("Failed to read state");
      return;
    }
    const j = await r.json();
    const current = j.state || "";
    if (current === "supplying") {
      const ru = await fetch(`${CP_BASE}/cp/${cpUid()}/unplug`, {
        method: "POST",
      });
      const txt = await ru.text();
      appendMessage("Action: unplug -> " + txt);
      // Unplug now ends the session: stop state polling so UI doesn't keep showing supplying
      stopStatePolling();
    } else {
      const rp = await fetch(`${CP_BASE}/cp/${cpUid()}/plug`, {
        method: "POST",
      });
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

// Explicit plug / unplug actions (separate buttons)
async function plug() {
  try {
    const rp = await fetch(`${CP_BASE}/cp/${cpUid()}/plug`, { method: "POST" });
    const txt = await rp.text();
    appendMessage("Action: plug -> " + txt);
    startStatePolling();
    await load();
    await pollMessages();
  } catch (e) {
    appendMessage("plug error: " + e.message);
  }
}

async function unplug() {
  try {
    const ru = await fetch(`${CP_BASE}/cp/${cpUid()}/unplug`, {
      method: "POST",
    });
    const txt = await ru.text();
    appendMessage("Action: unplug -> " + txt);
    // Unplug ends the session, stop polling to avoid stale supplying UI
    stopStatePolling();
    await load();
    await pollMessages();
  } catch (e) {
    appendMessage("unplug error: " + e.message);
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

// Ensure attachStopHandler exists (was previously removed) so other code can safely call it.
function attachStopHandler() {
  // make idempotent
  if (attachStopHandler._attached) return;
  attachStopHandler._attached = true;

  // Wire optional stop button if present in templates
  try {
    const btn = q("#btnStop");
    if (btn) {
      btn.addEventListener("click", () => {
        // stop simulation and polling
        stopSim();
        stopStatePolling();
        if (msgInterval) {
          clearInterval(msgInterval);
          msgInterval = null;
        }
        appendMessage("Stopped by user");
      });
    }

    // Ensure background timers are cleaned up on page unload
    window.addEventListener("beforeunload", () => {
      stopSim();
      stopStatePolling();
      if (msgInterval) {
        clearInterval(msgInterval);
        msgInterval = null;
      }
    });
  } catch (e) {
    // noop - defensive in case DOM isn't available
  }
}

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
// wire the new plug/unplug buttons
if (q("#btnPlug")) q("#btnPlug").onclick = plug;
if (q("#btnUnplug")) q("#btnUnplug").onclick = unplug;

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
  fetch(`${CP_BASE}/cp/${cpUid()}/telemetry?kWh=${energy}&power=${power}`, {
    method: "POST",
  }).catch((e) => console.error("Telemetry failed:", e));
}

// Wire input controls for CP API base if present in template
document.addEventListener("DOMContentLoaded", () => {
  const inp = q("#cp-api-base");
  const save = q("#cp-api-save");
  const reset = q("#cp-api-reset");
  if (inp) inp.value = CP_BASE;
  save?.addEventListener("click", () => {
    const v = inp.value?.trim();
    if (!v) return alert("Enter valid base URL");
    cpSetBase(v);
    alert("CP API base saved: " + CP_BASE);
  });
  reset?.addEventListener("click", () => {
    try {
      localStorage.removeItem("cp_api_base");
    } catch (e) {}
    CP_BASE = cpBuildBase();
    if (inp) inp.value = CP_BASE;
    alert("CP API base reset to: " + CP_BASE);
  });
});
