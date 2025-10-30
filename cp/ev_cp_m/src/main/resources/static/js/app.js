const q = (s) => document.querySelector(s);
const cpUid = () => q("#cpUid").value.trim();

// CP API base handling (accept port-only like "9901" or full URL)
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
  function normalize(input) {
    if (!input && input !== "") return input;
    const s = String(input).trim();
    if (!s) return null;
    if (/^\d+$/.test(s)) {
      try {
        return `${window.location.protocol}//${window.location.hostname}:${s}`;
      } catch (e) {
        return `http://localhost:${s}`;
      }
    }
    if (/^:\d+$/.test(s)) {
      const p = s.replace(/^:/, "");
      try {
        return `${window.location.protocol}//${window.location.hostname}:${p}`;
      } catch (e) {
        return `http://localhost:${p}`;
      }
    }
    if (/^https?:\/\//i.test(s)) return s;
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

async function refresh() {
  try {
    const res = await fetch(`${CP_BASE}/cp/${cpUid()}/state`);
    if (!res.ok) throw new Error("Error " + res.status);
    const data = await res.json();
    q("#output").textContent = JSON.stringify(data, null, 2);
  } catch (e) {
    q("#output").textContent = "Error: " + e.message;
  }
}

async function setState(state) {
  await fetch(`${CP_BASE}/cp/${cpUid()}/state`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ state }),
  });
  refresh();
}

async function plug() {
  await fetch(`${CP_BASE}/cp/${cpUid()}/plug`, { method: "POST" });
  refresh();
}

async function unplug() {
  await fetch(`${CP_BASE}/cp/${cpUid()}/unplug`, { method: "POST" });
  refresh();
}

q("#btnRefresh").onclick = refresh;
refresh();
