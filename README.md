# Seat Reservation Service

A high-concurrency seat booking system designed to handle massive stampedes without double-selling or 5xx errors.

## 🚀 How to Run

### Local Setup
1. Clone the repo.
2. Run `docker compose up --build`.
3. The service will be available at `http://localhost:8080`.

### API Usage

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

#### 2. Create a Show (Admin)
```bash
curl -X POST http://localhost:8080/shows \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Concert", "seats":["A1","A2","A3"], "price_paise":25000}'
```

#### 3. Reserve a Seat (User)
```bash
curl -X POST http://localhost:8080/shows/<SHOW_ID>/reserve \
  -H "Authorization: Bearer <USER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"seats":["A1"], "idempotency_key":"unique-key-1"}'
```

#### 4. Cancel Reservation (User)
```bash
curl -X POST http://localhost:8080/reservations/<RES_ID>/cancel \
  -H "Authorization: Bearer <USER_TOKEN>"
```

## 📈 Testing the Burst
To simulate a 20,000 request stampede against the live URL:
```bash
java Burst.java <BASE_URL>
```

## 🛠 Observability
- **Metrics**: `http://localhost:8080/actuator/prometheus`
- **Health**: `http://localhost:8080/actuator/health`
