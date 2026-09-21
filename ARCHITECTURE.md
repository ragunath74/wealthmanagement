# Real-Time Portfolio Rebalancing Platform — Architecture

Target scale: **10,000+ advisors**, **2,000,000 client accounts**, real-time reaction to market
price moves and model-portfolio changes, tax-loss-harvesting-aware trade generation, strict
consistency between "a trade was decided" and "a trade was queued for execution" — built as four
independently deployable microservices communicating over **gRPC** (synchronous reads) and
**Kafka** (asynchronous state changes), each owning its own MySQL schema.

## 1. The four services

```
                    ┌──────────────────┐         gRPC (unary + server-streaming)
                    │  account-service  │◀──────────────────────────────┐
                    │  :8081 REST       │                                │
                    │  :9091 gRPC       │───────┐                        │
                    │  MySQL:           │       │ Kafka:                 │
                    │  account_service  │       │ market.price.updates   │
                    └──────────────────┘       │ model.portfolio.updates │
                            ▲                   ▼                        │
                            │ Kafka:      ┌──────────────────────┐       │
                            │ trade.settled│  drift-engine-service │──────┘
                            └─────────────│  :8083 REST (audit)   │
                                          │  MySQL:                │────────┐ gRPC (unary)
                                          │  drift_engine_service  │        ▼
                                          └──────────────────────┘   ┌──────────────────────┐
                                                   │  ▲               │  tax-engine-service   │
                                    Kafka:          │  │ Kafka:        │  :9092 gRPC            │
                             trade.orders.outbound  │  │ account.      │  NO DATABASE           │
                                                     ▼  │ rebalance.    └──────────────────────┘
                                          ┌──────────────────────┐  requested
                                          │ execution-gateway-    │
                                          │ service                │
                                          │  :8084 REST (audit)    │
                                          │  MySQL:                 │
                                          │  execution_gateway_svc  │
                                          └──────────────────────┘
```

| Service | Owns | Exposes | Talks to |
|---|---|---|---|
| **account-service** | Advisor, Security, ModelPortfolio, ClientAccount, TaxLot | REST (CRUD/seeding) + gRPC `AccountService` | Kafka: consumes `market.price.updates`, `trade.settled`; produces `market.price.updates` (demo simulator), `model.portfolio.updates` |
| **tax-engine-service** | Nothing (stateless) | gRPC `TaxOptimizationService` only | Nothing — pure request→compute→response |
| **drift-engine-service** | TradeOrder, RebalanceRun (the decision + audit trail) | REST (audit read) | gRPC client to account-service and tax-engine-service; Kafka: consumes all 4 trigger topics, produces `account.rebalance.requested` and `trade.orders.outbound` |
| **execution-gateway-service** | ExecutionRecord | REST (audit read) | Kafka: consumes `trade.orders.outbound`, produces `trade.settled` |

## 2. Why this decomposition

Each boundary corresponds to a genuinely different *rate of change* and *failure domain*:

- **account-service** is the system of record. It changes only when an advisor acts (create an
  account, edit a model) or when a price tick lands. It's the one service every other service
  needs to read from, so it's built to be read-heavy and horizontally scalable on reads (gRPC
  server-streaming + keyset pagination, see §4).
- **tax-engine-service** is pure computation with zero state. Splitting it out means the trade-sizing
  policy (drift bands, wash-sale rules, cash constraints) can be scaled, tested, and deployed
  completely independently of anything that touches a database — and, not incidentally, it's the
  cheapest possible thing to horizontally scale, since any replica can answer any request.
- **drift-engine-service** is the orchestrator and the only place a "trade decision" is made. It's
  deliberately thin on business logic (that's tax-engine-service's job) and thick on
  coordination: locking, idempotency, calling out over gRPC, and writing the transactional outbox.
- **execution-gateway-service** is the boundary to the outside world (a real broker/FIX network in
  production). Isolating it means a slow or flaky downstream execution venue can degrade
  independently without taking the decision-making pipeline down with it.

## 3. gRPC vs. Kafka: which calls are which, and why

| Call | Transport | Why |
|---|---|---|
| drift-engine-service → account-service (read a snapshot / list accounts) | gRPC (unary / server-streaming) | Synchronous, needed immediately to make a decision; a request/response shape, not a state change. |
| drift-engine-service → tax-engine-service (get a trade list) | gRPC (unary) | Same reasoning — it's a pure function call across a process boundary. |
| drift-engine-service → execution-gateway-service (hand off a trade order) | **Kafka**, not gRPC | This is a *state change with a durability requirement* — if drift-engine-service crashes after deciding but before the order is queued, that's a lost trade. A gRPC call has no persistence if the caller dies mid-call; the transactional outbox (§5) guarantees the order is queued **eventually**, which a direct RPC cannot promise for free. |
| execution-gateway-service → account-service (settle a position) | **Kafka**, not gRPC | Same reasoning, and additionally: account-service must never be pushed into by two upstream services with two different consistency stories. It reads *one* event stream and decides for itself when it's safe to apply the mutation. |

The rule of thumb this platform follows: **gRPC for reads that need an answer right now; Kafka for
writes that need to survive a crash and fan out to more than one interested party.**

## 4. Preserved from the original single-service design (now spread across services)

The prompt's core scaling requirements are unchanged by the decomposition — only *where* each
mechanism lives changed:

- **Keyset (seek) pagination** — now implemented inside account-service's
  `ClientAccountRepository`/`TaxLotRepository` and exposed to the rest of the platform via the
  gRPC **server-streaming** RPCs `ListAccountIdsForModel` / `ListAccountIdsHoldingSecurity`
  (`AccountGrpcService`). Same property as before: `WHERE id > :lastSeenId ORDER BY id` keeps
  every page an index range scan regardless of scan depth, so a model change reaching 500,000
  accounts dispatches at a constant rate from the first account to the last. The only thing that
  changed is the transport — an in-process Java `List` became a gRPC stream of pages.
- **Transactional outbox** — now appears **twice**, once per service that makes a durable
  decision: drift-engine-service (`TradeOrder` + outbox row, same transaction) and
  execution-gateway-service (`ExecutionRecord` + outbox row, same transaction). Both reuse the
  exact same `OutboxEvent` entity and `OutboxRelayService` from the `common` module — see §6.
- **Three-layer idempotency/locking** — now maps cleanly onto service boundaries:
  1. **Redis distributed lock** per account (`common.lock.DistributedLockService`) — only
     drift-engine-service needs this, since it's the only service running a multi-step
     decision+persist pipeline that must never overlap for the same account.
  2. **Idempotency ledger** (`common.idempotency.ProcessedEvent`) — every service with a Kafka
     consumer has its own local copy of this table (account-service, drift-engine-service,
     execution-gateway-service). A redelivered Kafka message is a guaranteed no-op *within that
     service*, independent of what any other service does with the same event id.
  3. **Unique DB constraint** (`TradeOrder.idempotencyKey`, `ExecutionRecord.orderId`) — the
     backstop that holds even if the first two layers somehow both failed.
- **Optimistic locking (`@Version`)** — still on every mutable entity (`ClientAccount`, `TaxLot`,
  `Security`, `ModelPortfolio` in account-service; `TradeOrder` in drift-engine-service).
- **Price-move debounce** — still gates the price-driven fan-out on a minimum cumulative move
  (`rebalance.price-move-dispatch-threshold-bps`), now implemented in drift-engine-service's
  `PriceMoveGateService` using a Redis key per symbol rather than reading `Security.lastPrice`
  from a co-located database (drift-engine-service has no access to that table anymore).

## 5. Saga pattern and eventual consistency — the one thing that's genuinely different

The original single-service design mutated `TaxLot`/`cashBalance` **synchronously**, in the same
database transaction that decided the trade. That's no longer possible once "decide the trade"
(drift-engine-service) and "own the position" (account-service) are different databases —
there is no such thing as a cross-database ACID transaction here, and reaching for one (two-phase
commit / XA) would trade away availability and latency for a guarantee this domain doesn't
actually need instantaneously.

Instead, positions update via **choreography**: drift-engine-service decides and publishes;
execution-gateway-service fills and republishes; account-service applies the mutation when the
`trade.settled` event reaches it — asynchronously, and only then.

```
drift-engine-service          execution-gateway-service         account-service
  decide trade                                                  
  commit(TradeOrder, outbox)  
  relay -> trade.orders.outbound
                                simulate fill
                                commit(ExecutionRecord, outbox)
                                relay -> trade.settled  ───────────────▶ consume trade.settled
                                                                          mutate TaxLot/cash (idempotent)
  consume trade.settled (own group)
  mark TradeOrder FILLED
```

**This is a deliberate, discussable trade-off**, not an oversight:

- Between "trade decided" and "`trade.settled` applied," an account's position in account-service
  is briefly stale relative to the in-flight trade. A drift evaluation that runs in that window
  computes drift against the *pre-trade* position — in a real brokerage this window is exactly the
  settlement lag (T+1/T+2) that already exists for cash, so it's not introducing a new kind of
  staleness so much as modeling one that's already there.
- What this design does NOT protect against: two back-to-back trade decisions for the same
  account, straddling a not-yet-settled prior trade, computing drift against stale data and
  potentially over-trading. The per-account Redis lock (§4) prevents *concurrent* processing, not
  *sequential* processing against stale state. A production system would add either (a) a
  "pending trades" reservation that a fresh drift evaluation nets against before deciding, or
  (b) a minimum re-evaluation interval per account. This repo does neither — it's flagged here
  as the sharpest edge of the design, and the one most worth bringing up if you're using this
  project to talk through trade-offs in an interview.
- What this design DOES guarantee, unconditionally: no trade is ever decided-but-never-queued, no
  fill is ever recorded-but-never-announced (both outbox instances), and no settlement is ever
  double-applied (idempotency ledger, keyed by the same `idempotencyKey` end-to-end).

## 6. What's shared vs. duplicated, and why

The `common` Maven module holds `KafkaTopics`, the event payload records, `OutboxEvent` +
`OutboxRelayService`, `ProcessedEvent` + `IdempotencyService`, and `DistributedLockService`.
Every service that needs one of these depends on `common` and component-scans the specific
sub-packages it needs (see each service's `@SpringBootApplication(scanBasePackages = ...)`) —
nobody scans all of `common` blindly, so a service that doesn't need Redis locking (account-service,
execution-gateway-service) never even activates that autoconfiguration.

`GrpcServerLifecycle` (the ~60-line bean that boots a plain `io.grpc.Server` as a Spring
`SmartLifecycle`) is, by contrast, **duplicated** between account-service and tax-engine-service
rather than pulled into `common`. Sharing it would force tax-engine-service — which is
deliberately dependency-free (no JPA, no Kafka, no Redis; see its `pom.xml`) — to pull in
`common`'s Kafka/JPA/Redis dependencies for a 60-line class it doesn't otherwise need anything
else from. **Not every piece of near-identical code belongs in a shared library.** This is worth
having an opinion on going into a system-design interview: the reflex to eliminate all
duplication is usually wrong at a service boundary, because a shared library is also a shared
*deployment dependency* — every consumer now upgrades together, whether they want to or not.

## 7. Scaling to 2,000,000 accounts (unchanged reasoning, revisited)

- **Kafka partitioning is the parallelism budget.** `account.rebalance.requested` is keyed by
  `accountId`; partition count is the hard ceiling on how many `drift-engine-service` replicas can
  process rebalances in parallel. See `kafka/create-topics.sh` for the actual sizing math.
- **account-service's database is the platform's real bottleneck**, same as in the single-service
  design — this decomposition doesn't remove that, it just isolates it: only account-service's
  connection pool is under pressure from position reads/writes, and it can be scaled (read
  replicas, sharding by `account_id` hash) without touching the other three services at all. That
  is the actual payoff of the split, more than raw throughput: **independent scaling and
  independent failure** per bounded context.
- **tax-engine-service scales trivially** — stateless compute, any replica answers any request, no
  coordination needed between replicas at all.
- **execution-gateway-service** is the natural place a real system would add backpressure/circuit
  breaking against a flaky broker connection, isolated from the decision pipeline.

## 8. What's simplified for this codebase vs. a production build

- **No service discovery / mesh** — gRPC client channels and Kafka bootstrap servers are static
  config (`application.yml`). Production would resolve these through Kubernetes DNS or a mesh
  (Istio/Linkerd), and use mTLS instead of `usePlaintext()`.
- **No API gateway** — each service's REST surface is reachable directly (`:8081`–`:8084`). A
  production deployment would front these with a gateway for auth, rate limiting, and a single
  external hostname; nothing here needed a UI so it was left out per the request that produced
  this build.
- **The eventual-consistency gap in §5** is real and documented, not solved.
- **No circuit breaker / retry budget on the gRPC clients** in drift-engine-service — a slow or
  down tax-engine-service currently blocks that account's rebalance attempt (and, via the Redis
  lock's TTL, self-heals) rather than failing fast with a fallback.
- **No FIX protocol implementation** — execution-gateway-service logs what a real
  NewOrderSingle → ExecutionReport exchange would look like and simulates an immediate fill; there
  is no broker integration.
- **Authentication/authorization** is not implemented on any REST or gRPC surface.

## 9. Running it

See `RUNBOOK.md` for step-by-step instructions (infra bring-up, topic creation, running each
service, and a scripted walk through the full pipeline). See `INTERVIEW_GUIDE.md` for a
concept-by-concept map from "thing you might be asked about" to the exact file that demonstrates
it.
