# Technical Write-up: Seat Reservation Service

## 1. Atomic Decision Mechanism
To prevent double-selling under massive concurrency, the system uses a **Pessimistic Locking** strategy with a strict lock acquisition order.

**The Sequence:**
1. **User Row Lock**: First, we lock the user's entry in `user_show_holds` (`SELECT ... FOR UPDATE`). This serializes all requests from a single user, making the `per_user_limit` check exact.
2. **Sorted Seat Lock**: We resolve seat labels to IDs and lock them in **ascending order** (`SELECT ... FOR UPDATE ORDER BY seat_id`). 

**Why this works:**
- **No Double-Sell**: If 500 users race for seat A12, they all attempt to lock the same row. The database queues them. The first winner commits the status to `confirmed`. The subsequent 499 users, upon acquiring the lock, see the `confirmed` status and immediately decline with a `409`.
- **No Deadlocks**: By sorting `seat_id` ascending, we ensure that two users requesting the same set of seats in different orders (e.g., A1, A2 vs A2, A1) never create a circular wait.
- **Isolation**: `READ COMMITTED` isolation is used to ensure we see the most recent committed state while avoiding expensive gap locks.

## 2. Idempotency
Exactly-once semantics are enforced via a combination of database constraints and request hashing.
- **Storage**: The `reservations` table has a `UNIQUE(user_id, idempotency_key)`.
- **Verification**: We compute a `SHA-256` hash of the `show_id` and sorted `seats` list.
- **Logic**: 
    - If a key exists and the hash matches $\rightarrow$ return the original reservation (200).
    - If a key exists and the hash differs $\rightarrow$ reject with 409 (idempotency mismatch).
    - This check is performed twice: once as a "fast-path" (non-locking) and once authoritatively under the user lock.

## 3. Holds and Expiry
In this implementation, we use an **Explicit Cancel** model. Reservations are confirmed immediately. A seat is released only when the owner calls `/cancel`. 
*Future improvement*: A TTL-based hold could be implemented by adding a `expires_at` column and a background reaper task.

## 4. Consistency vs Availability
The system chooses **Consistency** (CP in CAP). By using a single MySQL primary, we ensure that no two people ever book the same seat. If the database is unavailable, the system refuses writes rather than risking a double-sell.

## 5. Observability & 2am Paging
I would be paged if:
- **5xx Error Rate > 0%**: Any server error during a burst indicates a regression in the locking logic.
- **Readiness Probe Fails**: If the DB is unreachable.
- **Invariant Drift**: If `available + confirmed != total_seats`.
- **Hikari Pool Exhaustion**: High `PendingConnections` in Prometheus, suggesting the `innodb_lock_wait_timeout` is too high or the pool is too small.

## 6. AI Usage
I used AI to generate the boilerplate and the initial structure. I manually designed the lock ordering, the idempotency hashing strategy, and the separate health-check pool to ensure the system survives a real-world stampede.
