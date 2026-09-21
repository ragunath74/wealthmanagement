# ==============================================================================
# Windows PowerShell equivalent of create-topics.sh. Creates every Kafka topic
# the platform needs, with partition counts sized to each topic's actual
# parallelism requirement -- see kafka/create-topics.sh for the full rationale
# comments (kept there to avoid duplicating them).
#
# Usage (with the docker-compose stack from the project root already running):
#   docker compose up -d kafka
#   powershell -File kafka/create-topics.ps1
# ==============================================================================

$ErrorActionPreference = "Stop"
$Bootstrap = "localhost:9092"

function New-Topic {
    param(
        [string]$Name,
        [int]$Partitions,
        [int]$Replication,
        [long]$RetentionMs
    )
    Write-Host "Creating topic: $Name (partitions=$Partitions, replication=$Replication)"
    docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server $Bootstrap --create --if-not-exists `
        --topic $Name `
        --partitions $Partitions `
        --replication-factor $Replication `
        --config "retention.ms=$RetentionMs"
}

New-Topic -Name "market.price.updates"        -Partitions 6  -Replication 1 -RetentionMs 3600000
New-Topic -Name "model.portfolio.updates"     -Partitions 3  -Replication 1 -RetentionMs 604800000
New-Topic -Name "account.rebalance.requested" -Partitions 12 -Replication 1 -RetentionMs 86400000
New-Topic -Name "trade.orders.outbound"       -Partitions 12 -Replication 1 -RetentionMs 604800000
New-Topic -Name "trade.settled"               -Partitions 12 -Replication 1 -RetentionMs 604800000

Write-Host ""
Write-Host "Done. Verify with:"
Write-Host "  docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server $Bootstrap --list"
