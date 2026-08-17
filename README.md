# Distributed E-Commerce Order Platform

Three Spring Boot services — **order**, **inventory**, **payment** — that place an
order without ever letting a slow downstream system block the customer. They talk
over Kafka for everything asynchronous and REST for the one thing that has to be
synchronous (pricing). Postgres holds the truth, Redis holds the things that are
cheap to lose.

```
                    POST /orders
                         │
                         ▼
                 ┌───────────────┐   GET /inventory/{sku}   ┌──────────────────┐
                 │ order-service │ ───────── REST ────────► │ inventory-service│
                 └───────┬───────┘                          └────────┬─────────┘
        202 Accepted ◄───┘                                           │
                         │                                           │
                         │  orders.created                           │
                         └──────────────────────────────────────────►│
                                                                     │
                         ◄──── inventory.reserved / .rejected ───────┘
                         │                │
                         │                │ inventory.reserved
                         │                ▼
                         │       ┌─────────────────┐
                         │       │ payment-service │
                         │       └────────┬────────┘
                         ◄─ payment.authorized / .declined
                         │
                         └─ inventory.release ─────────► (stock restored)
```

## Running it

Docker and Docker Compose are all you need — Java and Maven are only required if
you want to build outside a container.

```bash
docker compose up --build
```

Then, once the three services report healthy:

```bash
./scripts/demo.sh
```

The script walks the four behaviours that matter and prints what happened at each
step: a normal order, a repeated idempotency key, a declined payment being
compensated, and five buyers racing for one remaining unit.

| Service | Port | Try it |
| --- | --- | --- |
| order-service | 8081 | `curl localhost:8081/orders` |
| inventory-service | 8082 | `curl localhost:8082/inventory` |
| payment-service | 8083 | `curl localhost:8083/payments/{orderId}` |

Placing one by hand:

```bash
curl -X POST localhost:8081/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: my-key-1' \
  -d '{"customerId":"cust-1","lines":[{"sku":"SKU-LAPTOP","quantity":1}]}'
```

You get `202 Accepted` with an order id straight away. Poll
`GET /orders/{id}` and watch it move `PENDING → INVENTORY_RESERVED → CONFIRMED`.

## The four design decisions worth defending

### Intake never waits on payment

`POST /orders` prices the order, writes it, and returns. Reserving stock and
charging the card happen on Kafka topics afterwards. The fake gateway in
payment-service sleeps 800 ms by design — raise `GATEWAY_LATENCY_MS` to five
seconds and intake latency does not move. That is the entire argument for the
message bus, and it is easy to check rather than assert.

The order id is the Kafka record key, so everything about one order lands on one
partition and is handled in the order it happened, while unrelated orders spread
across partitions and consumers.

### Overselling is prevented by Postgres, not by Java

The reservation is a single conditional statement
([`InventoryRepository`](inventory-service/src/main/java/com/demo/orderplatform/inventory/store/InventoryRepository.java)):

```sql
UPDATE inventory
   SET available = available - ?, reserved = reserved + ?
 WHERE sku = ? AND available >= ?
```

The predicate lives in the `WHERE` clause. Postgres locks the row for the
duration of the update, so two transactions chasing the last unit are serialised
by the database — the loser re-evaluates against the already-decremented row and
matches zero rows. Reading the count first and deciding in application code is
exactly the race this avoids. A `CHECK (available >= 0)` sits underneath as a
backstop.

Multi-line orders are all-or-nothing: a shortfall on any line throws inside the
transaction and rolls back the lines already taken. Lines are locked in sorted
SKU order so two orders touching the same pair of SKUs cannot deadlock.

[`InventoryOversellIT`](inventory-service/src/test/java/com/demo/orderplatform/inventory/InventoryOversellIT.java)
runs 40 concurrent orders against 10 units of stock in a real Postgres container
and asserts exactly 10 winners.

### Duplicates are stopped in two different places, because they arrive two different ways

**From clients**, over HTTP: a retrying mobile app or an impatient load balancer
sends the same `POST` twice. The first request to claim the `Idempotency-Key` in
Redis via `SET NX` wins; every later one is handed back the order id the first
one created. If the write that the key was guarding fails, the key is released so
the client's retry is not told about an order that does not exist.

**From Kafka**, over the bus: delivery is at-least-once, so every consumer has to
be replay-safe on its own. Inventory claims a reservation row with
`ON CONFLICT DO NOTHING` before it decrements anything. Payments use `order_id`
as the primary key, which turns "charge this card twice" into a constraint
violation instead of a customer complaint. Order-service advances the saga with
guarded updates (`WHERE id = ? AND status IN (...)`), so a late or duplicated
event matches no row and quietly does nothing.

### Compensation, not distributed transactions

There is no two-phase commit across three databases. Order-service owns the saga
and is the only component that decides what an order becomes. Payment runs only
after stock is genuinely held, so the sole compensating action is putting stock
back — no refunds to issue. `inventory.release` is guarded by the same
status-transition trick, so a redelivered decline cannot restock twice.

## Where Redis is and is not used

Redis holds idempotency keys (24 h TTL) and caches catalog reads for 60 s. Both
are safe to lose: a cold cache is a Postgres query, and a lost key means one
duplicate order in the window. Reservations never consult the cache — a cached
stock count is a guess, and a decrement is not.

## Tests

```bash
mvn test      # unit tests, no Docker needed
mvn verify    # adds the Testcontainers integration tests, needs Docker
```

- `PaymentGatewayTest` — the decision rules.
- `OrderServiceTest` — idempotent replay, line folding, key release on failure.
- `InventoryOversellIT` — concurrency, replay safety, all-or-nothing rollback,
  and idempotent release, against a real Postgres.

## Deploying to AWS

The compose file maps to managed services one-for-one; see
[`deploy/aws.md`](deploy/aws.md). Each service is a stateless container with a
health endpoint and all configuration in environment variables, which is what
makes that swap uneventful.

## Known limits

Deliberate, and worth naming rather than hiding:

- **Publishing is after-commit, not transactional.** Events go out in an
  `AFTER_COMMIT` hook, so a crash in the gap between commit and publish loses the
  event. The fix is a transactional outbox table plus a relay; the seam for it is
  already there in `OrderEventPublisher`.
- **The idempotency claim is not linearisable with the write.** Two truly
  simultaneous requests with the same key can have the second one told about an
  order id whose row is still committing.
- **The dead-letter recoverer shares the JSON producer**, so records that fail
  *deserialization* are not republished faithfully. Fine for a demo, wrong for
  production.
- **The payment gateway is a sleep and two if-statements.** No real integration,
  no PCI surface.
- **Auth, rate limiting, and tracing are absent.** No API gateway, no JWTs, and
  no distributed tracing across the three hops.
# Distributed-E-Commerce-Order-Platform
