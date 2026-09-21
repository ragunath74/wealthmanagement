#!/usr/bin/env bash
# ==============================================================================
# Creates every Kafka topic the platform needs, with partition counts sized to
# each topic's actual parallelism requirement -- see the rationale comment
# above each `kafka-topics.sh --create` call. Auto-topic-creation is
# deliberately turned OFF in docker-compose.yml (KAFKA_AUTO_CREATE_TOPICS_ENABLE=false)
# so nothing can silently create a 1-partition topic by typo'ing a topic name;
# every topic that exists was created here, on purpose, with a partition count
# someone thought about.
#
# Usage (with the docker-compose stack from the project root already running):
#   docker compose up -d kafka
#   bash kafka/create-topics.sh
#
# Or, from Windows PowerShell: docker compose exec kafka bash -c "..."
# equivalents are in create-topics.ps1 in this same directory.
# ==============================================================================
set -euo pipefail

BOOTSTRAP="localhost:9092"
KAFKA_BIN="docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh"

create_topic() {
  local name="$1" partitions="$2" replication="$3" retention_ms="$4"
  echo "Creating topic: $name (partitions=$partitions, replication=$replication)"
  $KAFKA_BIN --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$name" \
    --partitions "$partitions" \
    --replication-factor "$replication" \
    --config "retention.ms=$retention_ms"
}

# market.price.updates -- keyed by symbol. Two independent consumer groups read
# it (account-service, drift-engine-service); 6 partitions is enough parallelism
# for a demo-sized universe of securities. Short retention: it's a live tick
# feed, not an audit log.
create_topic "market.price.updates" 6 1 3600000

# model.portfolio.updates -- keyed by model portfolio id. Low volume (an
# advisor/PM edits a model far less often than the market ticks), 3 partitions
# is headroom, not a bottleneck.
create_topic "model.portfolio.updates" 3 1 604800000

# account.rebalance.requested -- keyed by accountId. THIS topic's partition
# count is the platform's parallelism ceiling: it's the hard limit on how many
# drift-engine-service replicas can process rebalances concurrently (each
# partition is consumed by exactly one consumer instance within a consumer
# group). Sizing rule of thumb: partitions >= target throughput / per-partition
# throughput. If one instance processes ~50 accounts/sec end-to-end (two gRPC
# round trips + a DB write dominate), and the SLA is "a model change touching
# 500,000 accounts finishes in 10 minutes" (~833 accounts/sec), that's >=17
# partitions in production; 12 here is sized for a laptop demo, not that SLA --
# bump this before you'd trust it at 2M-account scale, and remember partition
# count can only go up, never down, without breaking the accountId->partition
# mapping other topics rely on for ordering.
create_topic "account.rebalance.requested" 12 1 86400000

# trade.orders.outbound -- keyed by accountId, consumed by execution-gateway-service.
create_topic "trade.orders.outbound" 12 1 604800000

# trade.settled -- keyed by accountId. Two independent consumer groups read it
# (account-service for position mutation, drift-engine-service for order
# status) -- same partition count as the topics upstream of it so a single
# account's full lifecycle (request -> order -> settlement) stays balanced
# across the same slice of the partition space.
create_topic "trade.settled" 12 1 604800000

echo ""
echo "Done. Verify with:"
echo "  docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP --list"
