# Runbook

Step-by-step: build everything, bring up infra, run all four services, and drive the full
pipeline end-to-end with plain HTTP calls (no UI — API access only, as requested).

Commands below are written for `bash` (Git Bash on Windows works fine, which is what these were
tested with). Where PowerShell differs, a `# PowerShell:` line follows.

## 0. Prerequisites

- JDK 17+
- Docker Desktop (for MySQL / Redis / Kafka)
- `curl` (ships with Windows 10+ as `curl.exe`, and with Git Bash)
- Optional: [`grpcurl`](https://github.com/fullstorydev/grpcurl) if you want to poke the gRPC
  APIs directly — every gRPC server has reflection enabled, so `grpcurl` needs no `.proto` files.

## 1. Build everything

```bash
./mvnw clean install
```

This builds the reactor in dependency order: `proto` (generates gRPC/protobuf Java from
`proto/src/main/proto/*.proto`) → `common` → the four services, running all unit tests along the
way (drift math, lot-selection strategies, wash-sale gating, cash-constrained sizing — 32 tests
across `tax-engine-service`'s compute package).

## 2. Bring up infrastructure

```bash
docker compose up -d
```

This starts MySQL, Redis, and Kafka. **MySQL auto-loads `sql/full-schema.sql` on its first boot**
(via `docker-entrypoint-initdb.d`), creating all three databases (`account_service`,
`drift_engine_service`, `execution_gateway_service`) and every table up front. Each service's own
Flyway migration then just baselines against what's already there on first startup (see
`spring.flyway.baseline-on-migrate` in each `application.yml`) — you don't need to do anything
extra for this to work.

Wait for MySQL to report healthy:

```bash
docker compose ps
```

## 3. Create Kafka topics

Auto-topic-creation is off on purpose (see `docker-compose.yml`), so this step is required:

```bash
bash kafka/create-topics.sh
# PowerShell: powershell -File kafka/create-topics.ps1
```

Verify:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

You should see all 5 topics: `market.price.updates`, `model.portfolio.updates`,
`account.rebalance.requested`, `trade.orders.outbound`, `trade.settled`.

## 4. Run all four services

Open four terminals (order matters a little — start tax-engine-service and account-service before
drift-engine-service, since it calls them over gRPC at request time, not at startup):

```bash
# Terminal 1
./mvnw -pl tax-engine-service spring-boot:run

# Terminal 2
./mvnw -pl account-service spring-boot:run

# Terminal 3
./mvnw -pl drift-engine-service spring-boot:run

# Terminal 4
./mvnw -pl execution-gateway-service spring-boot:run
```

Ports: account-service `:8081` (REST) / `:9091` (gRPC) · tax-engine-service `:9092` (gRPC only) ·
drift-engine-service `:8083` (REST) · execution-gateway-service `:8084` (REST).

Watch the logs across all four terminals as you drive the pipeline below — that's where the
concepts become visible: you'll see the gRPC calls land in tax-engine-service's log, the
`FIX 4.4 NewOrderSingle -> ExecutionReport` line in execution-gateway-service, the outbox relay
polling every 2 seconds, etc.

## 5. Drive the pipeline end-to-end

```bash
BASE=http://localhost:8081   # account-service

# 1. Create an advisor
ADVISOR_ID=$(curl -s -X POST $BASE/api/advisors \
  -H 'Content-Type: application/json' \
  -d '{"displayName":"Jane Advisor","email":"jane@example.com"}' | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "advisor: $ADVISOR_ID"

# 2. Create securities -- note XLC has a wash-sale replacement configured
curl -s -X POST $BASE/api/securities -H 'Content-Type: application/json' \
  -d '{"symbol":"VTI","name":"Vanguard Total Stock Market","initialPrice":250}'
curl -s -X POST $BASE/api/securities -H 'Content-Type: application/json' \
  -d '{"symbol":"BND","name":"Vanguard Total Bond Market","initialPrice":72}'
curl -s -X POST $BASE/api/securities -H 'Content-Type: application/json' \
  -d '{"symbol":"XLC","name":"Communication Services Sector","initialPrice":80,"washSaleReplacementSymbol":"XLC-ALT"}'
curl -s -X POST $BASE/api/securities -H 'Content-Type: application/json' \
  -d '{"symbol":"XLC-ALT","name":"Communication Services Alt","initialPrice":30}'

# 3. Create a 70/30 model portfolio
MODEL_ID=$(curl -s -X POST $BASE/api/model-portfolios -H 'Content-Type: application/json' \
  -d '{"name":"Growth 70/30","targets":[{"symbol":"VTI","targetWeight":0.7},{"symbol":"BND","targetWeight":0.3}]}' \
  | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "model: $MODEL_ID"

# 4. Create a taxable account against that model, with $5,000 cash
ACCOUNT_ID=$(curl -s -X POST $BASE/api/accounts -H 'Content-Type: application/json' \
  -d "{\"advisorId\":\"$ADVISOR_ID\",\"modelPortfolioId\":\"$MODEL_ID\",\"initialCashBalance\":5000,\"taxable\":true,\"taxLossHarvestingEnabled\":true}" \
  | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "account: $ACCOUNT_ID"

# 5. Seed an overweight, at-a-loss XLC position (bought at $100, now worth $80 -> harvest candidate)
curl -s -X POST $BASE/api/accounts/$ACCOUNT_ID/lots -H 'Content-Type: application/json' \
  -d '{"symbol":"XLC","quantity":20,"costBasisPerShare":100,"acquiredDate":"2023-01-15"}'

# 6. Trigger a rebalance by moving VTI's price -- watch all four terminals
curl -s -X POST $BASE/api/prices -H 'Content-Type: application/json' -d '{"symbol":"VTI","price":260}'
```

Give it a few seconds (the outbox relays poll every 2s, gRPC calls are fast but not instant), then
check what happened at each stage:

```bash
# drift-engine-service's decision + audit trail for this account
curl -s http://localhost:8083/api/accounts/$ACCOUNT_ID/trade-orders | jq .

# execution-gateway-service's simulated fills
curl -s http://localhost:8084/api/executions | jq .

# account-service's account after settlement -- cash balance should have moved
curl -s http://localhost:8081/api/accounts/$ACCOUNT_ID | jq .
```

You should see: a `TAX_LOSS_HARVEST` SELL on XLC (replaced by a BUY on XLC-ALT to preserve
exposure, wash-sale-safe), plus `DRIFT_REBALANCE` trades bringing VTI/BND back toward 70/30 —
generated by tax-engine-service, persisted by drift-engine-service, filled by
execution-gateway-service, and finally settled back into account-service's ledger.

### Trigger the other path: a model-portfolio change

```bash
curl -s -X PUT $BASE/api/model-portfolios/$MODEL_ID/targets -H 'Content-Type: application/json' \
  -d '{"targets":[{"symbol":"VTI","targetWeight":0.5},{"symbol":"BND","targetWeight":0.5}]}'
```

This fans out to every account mapped to the model via the gRPC server-streaming
`ListAccountIdsForModel` call — with one account it's not visually dramatic, but the same code
path is what handles 500,000 accounts in production.

## 6. Poke the gRPC APIs directly (optional)

```bash
grpcurl -plaintext localhost:9091 list
grpcurl -plaintext localhost:9091 wealthtech.account.v1.AccountService/GetAccountSnapshot \
  -d "{\"account_id\":\"$ACCOUNT_ID\"}"

grpcurl -plaintext localhost:9092 list
```

## 7. Shut down

```bash
docker compose down          # keep data
docker compose down -v       # also wipe MySQL/Redis/Kafka data (start clean next time)
```

## Troubleshooting

- **A service fails to start with a Flyway error about existing tables** — you likely ran
  `sql/full-schema.sql` by hand *and* let `docker-entrypoint-initdb.d` load it too, or you changed
  a migration file after a schema was already created from the old version. `docker compose down -v`
  and start over, or set `SPRING_FLYWAY_ENABLED=false` for that service if you're intentionally
  managing the schema by hand.
- **drift-engine-service logs `UNAVAILABLE: io exception` on startup-adjacent requests** —
  tax-engine-service or account-service isn't up yet; gRPC channels connect lazily on first call,
  so this resolves itself once both are listening.
- **A rebalance seems to do nothing** — check the drift threshold: the default is 5% (`rebalance.drift-threshold-bps=500`)
  and the minimum trade size is $25 — a tiny/well-balanced test account may genuinely have nothing
  to trade. Check `GET /api/accounts/{id}/trade-orders` on drift-engine-service either way; a
  `RebalanceRun` is always recorded even when it decides to skip, with a `skipReason`.
