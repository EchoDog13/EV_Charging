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
  tbody.innerHTML = list
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
