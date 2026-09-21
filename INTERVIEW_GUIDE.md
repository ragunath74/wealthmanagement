# Interview Guide

A map from "concept you might get asked about" to the exact place in this codebase that
demonstrates it, with enough of the "why" to actually explain it out loud. Organized roughly by
how a system-design interview tends to progress: requirements → high-level design → deep dives →
trade-offs.

---

## 1. Microservices decomposition

**Q: How did you decide the service boundaries?**

By rate-of-change and failure domain, not by CRUD entity. See `ARCHITECTURE.md` §2. The tell:
account-service (reference/position data) changes on advisor action or a price tick;
tax-engine-service (a pure function) never has state to change at all; drift-engine-service only
exists because *something* has to coordinate the other three; execution-gateway-service is the
boundary to an external system that can fail independently of everything else.

**Q: Why does tax-engine-service have no database?**

Because it doesn't need one — everything it needs to decide a trade (positions, targets, policy)
arrives on the request. A stateless service is the cheapest thing in the system to scale: any
replica answers any request, there's no cache-coherence problem, no connection-pool sizing
problem, nothing to migrate. See `tax-engine-service/pom.xml` — it has no JPA, no Kafka, no Redis
dependency at all, which is a *design statement*, not an oversight.

**Q: What would you do differently with more time?**

Add a circuit breaker (e.g. Resilience4j) around drift-engine-service's gRPC calls — right now a
down tax-engine-service just blocks that account's rebalance attempt until the Redis lock's TTL
expires, rather than failing fast with an explicit fallback. See `ARCHITECTURE.md` §8.

---

## 2. gRPC

**Files:** `proto/src/main/proto/*.proto`, `account-service/.../grpc/AccountGrpcService.java`,
`tax-engine-service/.../grpc/TaxEngineGrpcService.java`,
`drift-engine-service/.../config/GrpcClientConfig.java`,
`drift-engine-service/.../service/RebalanceDecisionService.java`.

**Q: Why gRPC instead of REST for service-to-service calls?**

Binary protobuf encoding (smaller, faster to (de)serialize than JSON), a strongly-typed contract
generated on both sides from one `.proto` file (no hand-maintained DTOs drifting out of sync
between services), and HTTP/2 multiplexing. The real win here specifically: **server-streaming**.

**Q: Where's the streaming RPC, and why?**

`AccountService.ListAccountIdsForModel` / `ListAccountIdsHoldingSecurity`
(`account.proto`) — server-streaming RPCs that internally keyset-paginate through up to 2M rows
and push pages to the caller as they're produced, rather than the caller waiting for one giant
response or making N round-trip request/response calls. `AccountGrpcService.listAccountIdsForModel`
shows the loop: query a page, `responseObserver.onNext(batch)`, repeat until the last page, then
`onCompleted()`. The consumer (`RebalanceDispatchService.dispatchForModelUpdate`) just iterates a
blocking-stub `Iterator<AccountIdBatch>` — the streaming is invisible complexity-wise on the
client side.

**Q: How does code generation work here?**

`protobuf-maven-plugin` in `proto/pom.xml`, configured with `protocArtifact` and `pluginArtifact`
(`io.grpc:protoc-gen-grpc-java`) pointing at Maven Central artifacts classified by OS/arch (via
the `os-maven-plugin` core extension in `.mvn/extensions.xml`) — so `protoc` itself is fetched as
a Maven dependency, no local `protoc` install required. `mvn compile` on the `proto` module
generates message classes + a `*Grpc` service base class (server) and stub classes (client) into
`target/generated-sources`.

**Q: How would a client discover what RPCs are available without the `.proto` file?**

`grpc-services`' `ProtoReflectionServiceV1`, registered in both servers'
`GrpcServerLifecycle.start()`. That's what lets `grpcurl -plaintext localhost:9091 list` work —
see `RUNBOOK.md` §6.

**Q: Why manually build the gRPC server instead of using a Spring Boot starter?**

Deliberately, for this project: a third-party starter (`net.devh:grpc-server-spring-boot-starter`,
etc.) would hide exactly the mechanics worth understanding for an interview —
`GrpcServerLifecycle` is ~60 lines showing a plain `io.grpc.ServerBuilder`, wired into Spring's
`SmartLifecycle` so it starts after the context (and DB pool) is up and stops cleanly on shutdown.

**Q: Unary vs. streaming — how do you decide?**

Unary when the answer is one bounded thing (`GetAccountSnapshot`, `GenerateTrades`).
Server-streaming when the answer is unboundedly large and the caller can usefully start working
before it's all arrived (paginated account-id lists). Client-streaming and bidi-streaming aren't
used here — there was no "many small requests building up to one response" or
"continuous two-way conversation" shape in this domain, but that's the next thing to reach for if
the interviewer asks.

---

## 3. Kafka

**Files:** `common/.../kafka/KafkaTopics.java`, `kafka/create-topics.sh`, every `*Listener.java`.

**Q: Walk me through your topics and why each one is partitioned the way it is.**

`kafka/create-topics.sh` has the rationale inline per topic. The one to lead with:
`account.rebalance.requested` is keyed by `accountId`, and its partition count is *the* hard
parallelism ceiling for the whole platform — see the sizing math in that script's comments
(target throughput ÷ per-partition throughput). Partition count can only go up, never down,
without breaking every downstream ordering guarantee that depends on the key→partition mapping,
so you size with headroom, not exactly.

**Q: Pub/sub vs. queue semantics — where does each appear?**

Both, on purpose, to make the distinction concrete:
- **Queue-like** (competing consumers): `account.rebalance.requested` has ONE consumer group
  (drift-engine-service instances split the partitions between them — each message processed once
  across the fleet).
- **Pub/sub-like** (fan-out): `market.price.updates` is consumed by account-service AND
  drift-engine-service under two *different* group ids — each gets its own full copy of the
  stream. Same mechanism (`market.price.updates` + `trade.settled`, which account-service and
  drift-engine-service also both independently subscribe to) is what makes Kafka simultaneously a
  work queue and an event bus: it's entirely which consumer group id you choose.

**Q: At-least-once or exactly-once? How do you know?**

At-least-once, everywhere, by design (`enable-auto-commit: false` + manual/record ack mode) —
paired with idempotent consumers (§5) rather than reaching for Kafka transactions/exactly-once
semantics, which add coordination cost this domain doesn't need once every consumer is already
idempotent. This is the standard "at-least-once delivery + idempotent processing = effectively
exactly-once effects" pattern.

**Q: Why is auto-topic-creation turned off?**

`docker-compose.yml`'s `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`. So every topic that exists was
created deliberately, with a partition count someone thought about — auto-creation silently gives
you a 1-partition topic on a typo'd topic name, which is a classic footgun in a system that's
supposed to scale on partition count.

---

## 4. The transactional outbox pattern

**Files:** `common/.../outbox/OutboxEvent.java`, `OutboxRelayService.java`, both usage sites:
`RebalanceTransactionService.persistDecision` (drift-engine-service) and `TradeOrderListener`
(execution-gateway-service).

**Q: What problem does this solve?**

The "dual write" problem: if you write a DB row and then call a message broker (or vice versa) as
two separate operations, a crash between them leaves the two systems disagreeing forever — a
trade that exists in the DB but was never queued for execution, or a message sent for a DB write
that got rolled back.

**Q: How does it solve it?**

Write the business row (`TradeOrder`) AND an `OutboxEvent` row **in the same local ACID
transaction**. A separate `@Scheduled` poller (`OutboxRelayService.relay()`, every 2s) reads
unsent rows, publishes them to Kafka, marks them sent. Now "the decision was persisted" and "the
decision will eventually be published" are the same fact, guaranteed by one transaction — at the
cost of at-least-once delivery (§3) rather than exactly-once.

**Q: Where does it appear more than once, and why does that matter?**

Twice: drift-engine-service (trade decision → `trade.orders.outbound`) and
execution-gateway-service (fill → `trade.settled`). Same `OutboxEvent` entity and
`OutboxRelayService` from `common`, reused, not reimplemented — demonstrating it's a general
pattern applicable anywhere a service makes a durable decision that needs to be announced, not a
one-off hack for one code path.

**Q: What's the known gap in your outbox relay implementation?**

`OutboxRelayService.relay()` is a plain `@Scheduled` method with no cross-instance coordination.
At single-instance scale it's fine; with multiple replicas of the same service, every instance
polls the same unsent-row batch — harmless (idempotent downstream) but wasteful. Production fix:
`SELECT ... FOR UPDATE SKIP LOCKED`, or partition the outbox table by `aggregate_id` hash across
dedicated relay instances. Documented in `ARCHITECTURE.md` §4 (mentioned there as a known
simplification).

---

## 5. Idempotency and exactly-once *effects*

**Files:** `common/.../idempotency/ProcessedEvent.java`, `IdempotencyService.java`, every
`*Listener.java`'s `tryClaim(...)` call.

**Q: How do you guarantee a redelivered Kafka message doesn't cause a duplicate side effect?**

`ProcessedEvent` has a unique primary key on `eventId`. `IdempotencyService.tryClaim` runs an
insert in its **own `REQUIRES_NEW` transaction** — a duplicate-key failure there is caught and
returns `false` ("already processed, skip"), and critically, this claim commits independently of
whatever larger business transaction the caller is about to run, so it can't be rolled back by an
unrelated failure later in that business logic.

**Q: Why REQUIRES_NEW specifically?**

If the idempotency claim were part of the same transaction as the business write, and that
business write later failed and rolled back, the claim would roll back too — meaning a redelivery
of the *same* message after a transient failure would look "unclaimed" again and get reprocessed,
which is correct for a message that genuinely failed, but subtly wrong if you *wanted* the claim
to survive a partial failure for audit purposes. `REQUIRES_NEW` here is a deliberate choice to
keep the two concerns (dedup bookkeeping vs. business outcome) independent.

**Q: Every service that consumes Kafka has its own `processed_event` table — why not one shared
table?**

Because idempotency is inherently *per-consumer*: "have I processed this" is a question about one
service's state, not a platform-wide fact. A shared table would be a shared-database anti-pattern
(§7) for no benefit — nothing is gained by letting execution-gateway-service see account-service's
dedup bookkeeping.

---

## 6. Distributed locking

**File:** `common/.../lock/DistributedLockService.java`, used in
`drift-engine-service/.../service/RebalanceOrchestrationService.java`.

**Q: Why do you need a lock if Kafka partitioning already guarantees one consumer per account?**

Under *normal* operation it doesn't add much — partitioning by `accountId` already means one
consumer thread handles all of one account's messages, in order. The lock earns its keep during
the brief windows where that guarantee is weaker: a consumer-group rebalance (partition
reassignment mid-flight can briefly cause two instances to both think they own a partition), or
operator error (two instances misconfigured into the same "exclusive" role). It's a safety net for
an edge case, not the primary correctness mechanism — say that explicitly if asked, because
claiming the lock *is* the primary mechanism is the wrong mental model.

**Q: How is the lock implemented, and what's the subtle bug it avoids?**

`SET key value NX PX <ttl>` to acquire (atomic: only succeeds if the key doesn't exist), and on
release, a Lua script that does a `GET` + conditional `DEL` — comparing the stored token before
deleting. Without that check, a lock holder whose TTL already expired (e.g. a long GC pause) could
delete a *different* process's now-current lock on the same key, letting two processes believe
they both hold exclusivity. The Lua script makes the check-and-delete atomic server-side, which a
plain `GET` in application code followed by `DEL` would not.

**Q: What's the backstop if the lock fails entirely (Redis down, TTL race)?**

The JPA `@Version` optimistic-locking column on every mutated entity — a genuinely concurrent
write fails fast with `ObjectOptimisticLockingFailureException` (mapped to HTTP 409) rather than
silently corrupting data. Three layers, each catching what the layer above it might miss — see
`ARCHITECTURE.md` §4.

---

## 7. Database-per-service vs. shared database

**Q: You said "shared MySQL instance, separate schemas" — isn't that a shared database, which
microservices are supposed to avoid?**

Two different things get conflated under "shared database": *shared infrastructure* (one MySQL
server process) vs. *shared schema/tables* (services reading/writing each other's tables
directly, or sharing a connection pool to the same logical database). This platform has the
former, not the latter — three separate logical databases (`account_service`,
`drift_engine_service`, `execution_gateway_service`), three separate credential sets in
principle, and critically, **no service's Hibernate mapping or SQL ever references a table
outside its own schema**. That's the property that actually matters for independent deployability
and independent schema evolution — see `sql/full-schema.sql`'s header comment and
`ARCHITECTURE.md` §1's table. In production this would more likely be separate DB instances
entirely, for failure isolation (a runaway query in one service can't starve another's connection
pool) — the shared instance here is a pragmatic concession to "run this on a laptop."

---

## 8. Saga pattern / eventual consistency

**File:** `ARCHITECTURE.md` §5 (read this section closely — it's the deepest trade-off in the
whole design), `account-service/.../kafka/TradeSettledListener.java`.

**Q: How do you keep data consistent across three databases without a distributed transaction?**

You don't try to — you replace one distributed ACID transaction with a **choreographed saga**:
each service does its own local transaction and announces what it did; the next service reacts.
Positions in account-service update *after* settlement, not in the same transaction as the trade
decision. This trades strong consistency for availability and simplicity (no 2PC coordinator, no
distributed lock manager spanning services) — appropriate here because the domain already has a
real-world analog to this lag (T+1/T+2 settlement).

**Q: What's the failure mode of this choice, concretely?**

Walk through §5's diagram: two rebalance triggers for the same account close together, the second
one evaluating drift against a position that hasn't yet absorbed the first trade's effect. The
per-account lock prevents *concurrent* double-processing, not *sequential* processing against
stale data. Naming this precisely (not just "eventual consistency is a trade-off" hand-waving) is
what separates a real answer from a buzzword.

---

## 9. Domain-specific: tax-lot accounting

**Files:** `tax-engine-service/.../compute/TaxLotSelectionService.java`,
`TaxLossHarvestingService.java`.

**Q: FIFO vs. LIFO vs. HIFO vs. "tax optimal" — what's the actual difference?**

All four decide *which specific purchase lot* to draw down when selling shares of a security you
bought at different times/prices. FIFO/LIFO are date-ordered (oldest/newest first) and
tax-blind. HIFO sells the highest-cost-basis lot first, which minimizes the *gain* recognized (or
maximizes the *loss*) — the tax-aware default. "Tax optimal" here specifically realizes lots
sitting at a loss first (to maximize this run's harvestable loss), falling back to HIFO once only
gain lots remain. See the `Comparator` chain in `TaxLotSelectionService.orderLots`.

**Q: Explain the wash-sale rule as you implemented it.**

IRC §1091: a realized loss is disallowed if you buy the same or a "substantially identical"
security within 30 days before or after the loss sale. Since the engine can't see the future, it
enforces this conservatively in both directions — see the class Javadoc on
`TaxLossHarvestingService`: (1) refuse to harvest if any lot of that security was bought within
the window (a recent purchase would disallow the loss anyway), and (2) after harvesting, never
re-buy the same security within the window — substitute a configured
`washSaleReplacementSymbol` (a correlated-but-not-identical security) to preserve market exposure
without triggering the rule.

**Q: What's explicitly NOT handled?**

Cross-account wash sales for the same taxpayer (the rule technically applies per-taxpayer, not
per-account — this engine only sees one account's lots) — called out directly in the class
Javadoc rather than silently ignored.
