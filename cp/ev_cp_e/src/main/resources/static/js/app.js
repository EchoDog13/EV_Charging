const q = (s) => document.querySelector(s);
const cpUid = () => q("#cpUid").value.trim();
let simInterval = null;
let msgInterval = null;
const maxMessages = 50;
let lastStateSeen = null;

async function load() {
  try {
    const r = await fetch(`/api/cp/${cpUid()}/state`);
    const j = await r.json();
    q("#output").textContent = JSON.stringify(j, null, 2);
  } catch (e) {
    q("#output").textContent = "Error loading: " + e.message;
  }
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
        return;
      }
    }
  } catch (e) {
    // ignore and try fallback
  }

  // Fallback: poll state and show a short note when state changes
  try {
    const r2 = await fetch(`/api/cp/${encodeURIComponent(cpUid())}/state`);
    if (r2.ok) {
      const j = await r2.json();
      const newState = j.state || JSON.stringify(j);
      if (newState !== lastStateSeen) {
        appendMessage("State: " + newState);
        lastStateSeen = newState;
      }
    }
  } catch (e) {
    // noop
  }
}

async function startSession() {
  // create a manual charge request for this CP via central/authorize-like endpoint
  const res = await fetch(`/api/cp/${cpUid()}/charge-requests?driverId=10`, {
    method: "POST",
  });
  const data = await res.json().catch(() => ({}));
  q("#output").textContent =
    "Session started:\n" + JSON.stringify(data, null, 2);
}

async function stopSession() {
  await fetch(`/api/cp/session/${cpUid()}/stop`, { method: "POST" });
  q("#output").textContent = "Session stopped.";
}

async function sendTelemetry() {
  // send telemetry by charger id (UI-friendly)
  const energy = Math.random() * 20 + 5;
  const power = Math.random() * 10 + 2;
  await fetch(`/api/cp/${cpUid()}/telemetry?kWh=${energy}&power=${power}`, {
    method: "POST",
  });
  q("#output").textContent = "Telemetry sent.";
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
load();

// Start polling messages every 2s
if (!msgInterval) msgInterval = setInterval(pollMessages, 2000);
