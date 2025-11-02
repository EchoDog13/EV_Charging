# ⚡ EV Charging Practice 1

A simulation system for managing a network of electric vehicle (EV) charging stations. The project models a **Central Control Server**, **Charging Points (CPs)**, **Drivers**, and a **Kafka event broker** to demonstrate distributed systems design, real-time communication, and frontend/backend integration.

JAVA MUST BE COMPLIED ON EACH MACHINE BEFORE EACH RUN

USE: ./mvnw clean package -DskipTests -e

---

## 📌 Scope

The system simulates:

- A **Central Control Panel** for monitoring and managing EV charging stations.
- **Charging Points (CPs)** that register with the central system and simulate charging sessions.
- **Drivers** who request and manage charging sessions.
- **Kafka** as the event-streaming backbone for communication between all components.

---

## 🏗️ Architecture

The system is composed of four main components, each running separately:

1. **Central Server**
   - Backend + frontend for central control
   - MySQL database
   - REST API for management
2. **Kafka Server**
   - Event queue and communication manager
   - Runs on port `9092`
3. **Charging Points Machine**
   - Hosts 0..n charging points, each on its own socket
   - Each CP has a **monitor** (status, registration) and an **engine** (charging simulation)
   - Frontend per CP
4. **Drivers Machine**
   - Hosts 0..n drivers
   - Each driver has a frontend to request/manage charging

---

## 🎯 Features

### Central

- Register and track CPs (new or previously registered)
- Display CP info: UID, location, price, state (color-coded)
- Authorize charge requests
- Stop/resume charging sessions (per CP or all at once)
- Display system messages (registrations, state changes, etc.)

### Charging Points

- **Monitor**: register with central, send health status, display state
- **Engine**: simulate plug/unplug, start/stop charging, send telemetry

### Drivers

- Request charging manually or via simulation (file-driven, 4s intervals)
- View available CPs
- Track charging status
- Receive final billing ticket (CP UID, total, cost per kWh)

### Kafka

- Event-driven communication between Central, CPs, and Drivers
- Topics:
  - `cp.command` → Central → CP
  - `charge.request` → Driver/CP → Central
  - `charge.auth` → Central → Driver + CP
  - `charge.session` → CP → Central + Driver
  - `billing.ticket` → Central → Driver

---

## 🌐 API Endpoints

### Central Service

- `GET /central/cps` → List all CPs
- `GET /central/cps/{cpUid}` → CP details
- `POST /central/cps/{cpUid}/state` → Change CP state
- `POST /central/cps/commands/stop-all` → Stop all CPs
- `POST /central/charge-requests` → Authorize driver request
- `GET /central/sessions` → List sessions
- `POST /central/sessions/{sessionId}/end` → End session
- `GET /central/tickets/{sessionId}` → Retrieve billing ticket
- `GET /central/messages` → System messages

### Charging Point

- `POST /cp/{cpUid}/state` → Update CP state
- `POST /cp/{cpUid}/charge-requests` → Manual charge request
- `POST /cp/{cpUid}/plug` → Plug in vehicle
- `POST /cp/{cpUid}/unplug` → Unplug vehicle
- `POST /cp/session/{sessionId}/start` → Start session
- `POST /cp/session/{sessionId}/telemetry` → Send telemetry
- `POST /cp/session/{cpUid}/stop` → Stop session

### Driver

- `GET /driver/cps` → List available CPs
- `POST /driver/charge-requests` → Request charging
- `GET /driver/sessions/{sessionId}` → Session details
- `POST /driver/sessions/{sessionId}/stop` → Stop session
- `POST /driver/simulations/start` → Start simulation
- `POST /driver/simulations/stop` → Stop simulation
- `GET /driver/tickets/{sessionId}` → Retrieve ticket

---

## 🔄 Real-Time Updates

- **Server-Sent Events (SSE)** are used to push live updates (e.g., consumption data, session status) to the frontend without manual refresh.

---

## 🎨 Frontend

- Implemented with **Spring Boot + REST API**
- Basic CSS for layout and color-coded states:
  - Green → Available / Supplying
  - Orange → Stopped
  - Red → Out of Order
  - Grey → Disconnected

---

## 📦 Deployment

- Minimum of **3 machines**:
  - PC1: Charging Points
  - PC2: Central + Kafka
  - PC3: Drivers
- **Docker + Docker Compose** used for containerized deployment.

---

## 📅 Timeline

- **Week 1 (6/10/25):** Develop components
- **Week 2 (13/10/25):** Integrate + frontend development
- **Week 3 (20/10/25):** In-lab testing & troubleshooting
- **Week 4 (27/10/25):** Demonstration

---

## 👥 Team & Work Distribution

- **Central**
  - Backend & API: _Kyle_
  - Frontend: _Sufyan_
- **Charging Points**
  - Backend & API: _Kyle_
  - Frontend: _Sufyan_
- **Driver**
  - Backend & API: _Sufyan_
  - Frontend: _Sufyan_
- **Final Implementation Guide:** Kyle & Sufyan

Collaboration via **GitHub**.

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven
- Docker & Docker Compose
- MySQL
- Kafka

### Build & Run

```bash
# Clone repo
git clone https://github.com/<your-org>/EV_Charging.git
cd EV_Charging

# Build with Maven
mvn clean install

# Run with Docker Compose
docker-compose up --build
```
