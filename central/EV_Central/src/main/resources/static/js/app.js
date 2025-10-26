const tbody = document.querySelector('#tbl tbody');

function pill(state){ return `<span class="pill ${state}">${state}</span>`; }
function fmt(ts){ return ts? new Date(ts).toLocaleString():"—"; }

async function load(){
  const res = await fetch('/api/chargers');
  const data = await res.json();
  render(data);
}

function render(list){
  tbody.innerHTML = list.map(ch => `
    <tr data-uid="${ch.uid}">
      <td>${ch.uid}</td>
      <td>${ch.location||''}</td>
      <td>${(ch.pricePerKWh??0).toFixed(2)}</td>
      <td class="state">${pill(ch.state)}</td>
      <td class="last">${fmt(ch.lastHealthCheck)}</td>
    </tr>`).join('');
}

const es = new EventSource('/api/stream');
es.addEventListener('charger', ev => {
  const ch = JSON.parse(ev.data);
  const tr = tbody.querySelector(`tr[data-uid="${ch.uid}"]`);
  if (tr){
    tr.querySelector('.state').innerHTML = pill(ch.state);
    tr.querySelector('.last').textContent = fmt(ch.lastHealthCheck);
  } else {
    load();
  }
});

load();