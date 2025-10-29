const API_BASE = "http://192.168.100.100:9900";

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
    </tr>`
    )
    .join("");
}

// Poll every 5 seconds
load().catch(console.error);
setInterval(() => load().catch(console.error), 5000);

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
