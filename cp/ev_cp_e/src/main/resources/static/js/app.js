const q = (s) => document.querySelector(s);
const cpUid = () => q("#cpUid").value.trim();
let simInterval = null;

async function load() {
  try {
    const r = await fetch(`/api/cp/${cpUid()}/state`);
    const j = await r.json();
    q("#output").textContent = JSON.stringify(j, null, 2);
  } catch (e) {
    q("#output").textContent = "Error loading: " + e.message;
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

q("#btnLoad").onclick = load;
load();
