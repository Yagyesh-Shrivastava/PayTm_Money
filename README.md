# 🎟️ High-Concurrency Seat Reservation System

A professional, production-ready seat booking engine designed for Paytm Money. This system is engineered to handle massive "stampedes" (thousands of concurrent requests) while guaranteeing **zero double-selling** and strict enforcement of per-user booking limits.

## 🎯 Core Objectives
- **Zero Double-Selling**: Absolute guarantee that one seat = one user.
- **Per-User Limits**: Strictly enforce `per_user_limit` even when a user sends 100 simultaneous requests.
- **Idempotency**: Ensure that network retries do not create duplicate reservations.
- **Observability**: Full Prometheus metrics and isolated health checks to prevent "death spirals" during pool saturation.

---

## 🛠️ Technical Architecture

### 1. Atomic Decision Mechanism
To prevent race conditions, the system implements a **Strict Locking Hierarchy**. Every reservation transaction follows this exact sequence:

1.  **User Row Lock**: Locks the user's record in `user_show_holds` (`SELECT ... FOR UPDATE`). This serializes all requests from a single user, making the limit check atomic.
2.  **Sorted Seat Lock**: Resolves seat labels to IDs and locks them in **ascending order** (`SELECT ... FOR UPDATE ORDER BY seat_id`). 

**Why this design?**
- **Prevents Double-Sells**: The database queues all requests for a specific seat. Only the first winner commits; others see the status as `confirmed` and are rejected.
- **Eliminates Deadlocks**: By sorting `seat_id` ascending, we ensure that users requesting the same seats in different orders never create a circular wait.

### 2. Exactly-Once Semantics (Idempotency)
We use a combination of database constraints and request hashing:
- **Unique Constraint**: `UNIQUE(user_id, idempotency_key)` prevents duplicate entries.
- **Request Hashing**: A `SHA-256` hash of the `show_id` and sorted `seats` list is stored.
- **Verification**:
    - Match (Key & Hash) $\rightarrow$ Returns original reservation (Success).
    - Mismatch (Key exists, Hash differs) $\rightarrow$ Rejects with `409 Conflict`.

### 3. Observability & Stability
- **Isolated Health Pool**: A dedicated Hikari connection pool for `/actuator/health` ensures that if the main reservation pool is saturated, the health check still works, preventing Kubernetes/Load Balancers from killing the pod unnecessarily.
- **Jittered Backoff**: When `PessimisticLockingFailureException` occurs, the system retries with a random delay (`10ms + rand(50ms)`) to prevent the "thundering herd" effect.
- **Prometheus Metrics**: Tracks `confirmed_reservations`, `declined_per_user_limit`, `declined_seat_taken`, and `idempotency_replays`.

---

## 🚀 Quick Start

### Local Setup
1.  **Clone the repo**
2.  **Start Infrastructure**:
    ```bash
    docker compose up --build
    ```
3.  **Access**: `http://localhost:8080`

### API Guide

#### 1. Authentication
**Get User Token:**
```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"user_id":"user123"}'
```

**Get Admin Token:**
```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"user_id":"admin", "X-Admin-Secret":"admin-super-secret-key"}'
```

#### 2. Admin: Create a Show
```bash
curl -X POST http://localhost:8080/shows \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Concert", "seats":["A1","A2","A3"], "price_paise":25000, "per_user_limit":2}'
```

#### 3. User: Reserve Seats
```bash
curl -X POST http://localhost:8080/shows/<SHOW_ID>/reserve \
  -H "Authorization: Bearer <USER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"seats":["A1"], "idempotency_key":"unique-key-1"}'
```

#### 4. User: Cancel Reservation
```bash
curl -X POST http://localhost:8080/reservations/<RES_ID>/cancel \
  -H "Authorization: Bearer <USER_TOKEN>"
```

---

## 📈 Testing the "Stampede"
We provide a professional simulation script `Burst.java` to verify the system's correctness under extreme load.

**Run Simulation:**
```bash
java Burst.java <BASE_URL>
```
This script simulates thousands of concurrent requests for the same "hot seats" to prove that no double-selling occurs and that the system remains responsive.

## 🛠 Monitoring
- **Prometheus Metrics**: `http://localhost:8080/actuator/prometheus`
- **Health Status**: `http://localhost:8080/actuator/health`
