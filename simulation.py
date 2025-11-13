import requests
import time
import json
from typing import List, Tuple

# ================= CONFIGURATION =================
DRIVER_IP = "http://100.74.162.58:7040"   # Driver API
CP_IP     = "http://100.83.66.30:9900"   # CP API

# Simulation parameters
CP_UID = "CP001"
DRIVER_ID = "DRIVER123"
NUM_SIMULATIONS = 3

# Endpoints
CHARGE_REQUEST_URL = f"{DRIVER_IP}/driver/charge-requests/{CP_UID}/driver/{DRIVER_ID}"
PLUG_IN_URL = f"{CP_IP}/cp/{CP_UID}/plug"
UNPLUG_URL = f"{DRIVER_IP}/driver/sessions/{CP_UID}/stop"
CP_STATE_URL = f"{CP_IP}/cp/{CP_UID}/state"

# ================================================

def print_response(title: str, response: requests.Response):
    print(f"\n=== {title} ===")
    print(f"Status: {response.status_code}")
    try:
        print(f"Response: {json.dumps(response.json(), indent=2)}")
    except:
        print(f"Response: {response.text}")

def wait_with_status(seconds: int, message: str):
    print(f"{message} (waiting {seconds}s)...")
    time.sleep(seconds)

def simulate_one_cycle(sim_id: int):
    print(f"\n{'='*60}")
    print(f"STARTING SIMULATION #{sim_id}")
    print(f"{'='*60}")

    # 1. DRIVER: Send charge request
    print(f"[{sim_id}.1] Sending charge request from driver...")
    charge_resp = requests.post(CHARGE_REQUEST_URL)
    print_response("Charge Request", charge_resp)

    # 2. CP: Simulate plug-in
    wait_with_status(2, "[Waiting 2s before plug-in]")
    print(f"[{sim_id}.2] Plugging in vehicle at CP...")
    plug_resp = requests.post(PLUG_IN_URL)
    print_response("Plug In", plug_resp)

    # 3. Wait 10 seconds (simulate charging)
    wait_with_status(10, "[Charging in progress...]")

    # Optional: Check CP state during charging
    state_resp = requests.get(CP_STATE_URL)
    print_response("CP State (during charging)", state_resp)

    # 4. DRIVER: Unplug to end session
    print(f"[{sim_id}.3] Unplugging to end session...")
    unplug_resp = requests.post(UNPLUG_URL)
    print_response("Unplug / Stop Session", unplug_resp)

    # Final state
    time.sleep(1)
    final_state = requests.get(CP_STATE_URL)
    print_response("Final CP State", final_state)

    print(f"SIMULATION #{sim_id} COMPLETED\n")

def main():
    print("EV Charging Simulation Starting...")
    print(f"Driver API: {DRIVER_IP}")
    print(f"CP API:     {CP_IP}")
    print(f"CP UID:     {CP_UID}")
    print(f"Driver ID:  {DRIVER_ID}")
    print(f"Simulations: {NUM_SIMULATIONS}\n")

    for i in range(1, NUM_SIMULATIONS + 1):
        simulate_one_cycle(i)
        if i < NUM_SIMULATIONS:
            wait_with_status(4, f"Waiting 4s before next simulation")

    print("ALL SIMULATIONS COMPLETED.")

if __name__ == "__main__":
    main()