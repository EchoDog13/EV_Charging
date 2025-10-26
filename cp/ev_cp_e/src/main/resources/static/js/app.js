const q = (s)=>document.querySelector(s);
const cpUid = ()=> q('#cpUid').value.trim();
let simInterval = null;

async function load(){
  try{
    const r = await fetch(`/cp/${cpUid()}/state`);
    const j = await r.json();
    q('#output').textContent = JSON.stringify(j, null, 2);
  }catch(e){
    q('#output').textContent = 'Error loading: '+e.message;
  }
}

async function startSession(){
  const res = await fetch(`/cp/session/start`, {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({ cpUid: cpUid(), driverId: 10 })
  });
  const data = await res.json().catch(()=>({}));
  q('#output').textContent = 'Session started:\n' + JSON.stringify(data,null,2);
}

async function stopSession(){
  await fetch(`/cp/session/${cpUid()}/stop`, { method:'POST' });
  q('#output').textContent = 'Session stopped.';
}

async function sendTelemetry(){
  await fetch(`/cp/session/${cpUid()}/telemetry`, {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({
      energy: Math.random()*20 + 5,
      power: Math.random()*10 + 2,
      timestamp: Date.now()
    })
  });
  q('#output').textContent = 'Telemetry sent.';
}

function simulate(){
  if(simInterval) return;
  q('#output').textContent = 'Simulation running...';
  simInterval = setInterval(sendTelemetry, 4000);
}

function stopSim(){
  if(simInterval){
    clearInterval(simInterval);
    simInterval = null;
    q('#output').textContent = 'Simulation stopped.';
  }
}

q('#btnLoad').onclick = load;
load();