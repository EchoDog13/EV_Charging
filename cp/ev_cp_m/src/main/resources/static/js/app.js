const q = (s)=>document.querySelector(s);
const cpUid = ()=> q('#cpUid').value.trim();

async function refresh(){
  try{
    const res = await fetch(`/cp/${cpUid()}/state`);
    if(!res.ok) throw new Error('Error '+res.status);
    const data = await res.json();
    q('#output').textContent = JSON.stringify(data, null, 2);
  }catch(e){
    q('#output').textContent = 'Error: ' + e.message;
  }
}

async function setState(state){
  await fetch(`/cp/${cpUid()}/state`, {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({state})
  });
  refresh();
}

async function plug(){
  await fetch(`/cp/${cpUid()}/plug`, {method:'POST'});
  refresh();
}

async function unplug(){
  await fetch(`/cp/${cpUid()}/unplug`, {method:'POST'});
  refresh();
}

q('#btnRefresh').onclick = refresh;
refresh();