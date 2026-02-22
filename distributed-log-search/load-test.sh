#!/usr/bin/env bash
# ============================================================
# Distributed Log Search — Load Test Script
#
# Simulates realistic log traffic to validate:
#   - 50K+ events/min throughput
#   - Sub-300ms search latency
#   - Fault tolerance under load
#
# Usage:
#   chmod +x load-test.sh
#   ./load-test.sh [INGESTION_URL] [SEARCH_URL] [DURATION_SECONDS]
#
# Examples:
#   ./load-test.sh                              # defaults
#   ./load-test.sh http://localhost:8081 http://localhost:8083 60
# ============================================================

INGESTION_URL="${1:-http://localhost:8081}"
SEARCH_URL="${2:-http://localhost:8083}"
DURATION="${3:-30}"
BATCH_SIZE=100
CONCURRENT_WORKERS=10
SEARCH_WORKERS=3

# Colors
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

# Counters
INGESTED=0; FAILED_INGEST=0; SEARCHES=0; FAILED_SEARCH=0
START_TIME=$(date +%s)

echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    Distributed Log Search — Load Test            ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "Ingestion URL : ${GREEN}${INGESTION_URL}${NC}"
echo -e "Search URL    : ${GREEN}${SEARCH_URL}${NC}"
echo -e "Duration      : ${YELLOW}${DURATION}s${NC}"
echo -e "Batch size    : ${YELLOW}${BATCH_SIZE}${NC}"
echo -e "Workers       : ${YELLOW}${CONCURRENT_WORKERS}${NC}"
echo ""

# ── Service Availability Check ────────────────────────────────

echo -e "${YELLOW}Checking service availability...${NC}"

if ! curl -sf "${INGESTION_URL}/api/v1/logs/health" > /dev/null 2>&1; then
    echo -e "${RED}✗ Ingestion service not reachable at ${INGESTION_URL}${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Ingestion service is UP${NC}"

if ! curl -sf "${SEARCH_URL}/api/v1/search/health" > /dev/null 2>&1; then
    echo -e "${RED}✗ Search API not reachable at ${SEARCH_URL}${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Search API is UP${NC}"
echo ""

# ── Log Generation ────────────────────────────────────────────

SERVICES=("payment-service" "auth-service" "user-service" "inventory-service" "notification-service" "api-gateway")
LEVELS=("INFO" "INFO" "INFO" "DEBUG" "WARN" "ERROR")  # Weighted: INFO most common
HOSTS=("prod-node-01" "prod-node-02" "prod-node-03" "prod-node-04")

MESSAGES=(
    "Request processed successfully userId=\$RANDOM in \$((RANDOM % 200 + 10))ms"
    "Database query executed table=orders rows=\$((RANDOM % 1000)) duration=\$((RANDOM % 50))ms"
    "Cache hit ratio=\$((RANDOM % 40 + 60))% key=session:\$RANDOM"
    "HTTP \$((RANDOM % 3 == 0 ? 500 : 200)) POST /api/v1/orders latency=\$((RANDOM % 300))ms"
    "Rate limit approaching threshold=80% ip=192.168.\$((RANDOM % 255)).\$((RANDOM % 255))"
    "Connection pool exhausted maxSize=100 waiting=\$((RANDOM % 20 + 5))"
    "JWT token validation failed: expired userId=\$RANDOM"
    "Circuit breaker OPEN for downstream-service after 5 failures"
    "Retry attempt 3/3 for payment gateway timeout"
    "GC overhead: pauseMs=\$((RANDOM % 500)) type=G1GC"
    "Kafka consumer lag partition=\$((RANDOM % 6)) lag=\$((RANDOM % 10000))"
    "Elasticsearch replication lag shards=\$((RANDOM % 3 + 1))"
)

generate_batch() {
    local service="${SERVICES[$((RANDOM % ${#SERVICES[@]}))]}"
    local host="${HOSTS[$((RANDOM % ${#HOSTS[@]}))]}"

    # Build JSON array of logs
    local logs="["
    for i in $(seq 1 $BATCH_SIZE); do
        local level="${LEVELS[$((RANDOM % ${#LEVELS[@]}))]}"
        local msg="${MESSAGES[$((RANDOM % ${#MESSAGES[@]}))]}"
        local trace_id=$(printf '%016x' $RANDOM$RANDOM)
        local ts=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")

        logs+="{\"level\":\"${level}\",\"message\":\"${msg}\",\"timestamp\":\"${ts}\",\"traceId\":\"${trace_id}\"}"
        if [ $i -lt $BATCH_SIZE ]; then logs+=","; fi
    done
    logs+="]"

    echo "{\"serviceName\":\"${service}\",\"host\":\"${host}\",\"logs\":${logs}}"
}

# ── Ingestion Worker ──────────────────────────────────────────

ingestion_worker() {
    local worker_id=$1
    local local_ingested=0
    local local_failed=0
    local end_time=$((START_TIME + DURATION))

    while [ $(date +%s) -lt $end_time ]; do
        payload=$(generate_batch)

        response=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST "${INGESTION_URL}/api/v1/logs/batch" \
            -H "Content-Type: application/json" \
            -d "$payload" \
            --max-time 5 2>/dev/null)

        if [ "$response" = "202" ]; then
            local_ingested=$((local_ingested + BATCH_SIZE))
        else
            local_failed=$((local_failed + 1))
        fi

        # Small sleep to avoid immediate retry on failures
        if [ "$response" != "202" ]; then sleep 0.1; fi
    done

    echo "worker:${worker_id},ingested:${local_ingested},failed:${local_failed}" >> /tmp/load_test_results.txt
}

# ── Search Worker ─────────────────────────────────────────────

search_worker() {
    local worker_id=$1
    local local_searches=0
    local local_failed=0
    local total_latency=0
    local end_time=$((START_TIME + DURATION))

    QUERIES=("ERROR" "timeout" "Connection refused" "NullPointerException" "Database" "payment")
    SEARCH_SERVICES=("payment-service" "auth-service" "user-service")

    while [ $(date +%s) -lt $end_time ]; do
        query="${QUERIES[$((RANDOM % ${#QUERIES[@]}))]}"
        service="${SEARCH_SERVICES[$((RANDOM % ${#SEARCH_SERVICES[@]}))]}"

        # Random search type
        case $((RANDOM % 3)) in
            0) url="${SEARCH_URL}/api/v1/search?query=${query}&size=25" ;;
            1) url="${SEARCH_URL}/api/v1/search?serviceName=${service}&levels=ERROR&size=25" ;;
            2) url="${SEARCH_URL}/api/v1/search?query=${query}&serviceName=${service}&size=10" ;;
        esac

        start_ms=$(($(date +%s%3N)))
        response=$(curl -s -o /dev/null -w "%{http_code}" "$url" --max-time 5 2>/dev/null)
        end_ms=$(($(date +%s%3N)))
        latency=$((end_ms - start_ms))

        if [ "$response" = "200" ]; then
            local_searches=$((local_searches + 1))
            total_latency=$((total_latency + latency))
        else
            local_failed=$((local_failed + 1))
        fi

        sleep 0.5  # 2 searches/sec per worker
    done

    local avg_latency=0
    if [ $local_searches -gt 0 ]; then
        avg_latency=$((total_latency / local_searches))
    fi

    echo "search_worker:${worker_id},searches:${local_searches},failed:${local_failed},avg_latency:${avg_latency}" >> /tmp/load_test_results.txt
}

# ── Run Test ──────────────────────────────────────────────────

rm -f /tmp/load_test_results.txt
echo -e "${YELLOW}Starting load test for ${DURATION} seconds...${NC}"
echo ""

# Start ingestion workers in background
for i in $(seq 1 $CONCURRENT_WORKERS); do
    ingestion_worker $i &
done

# Start search workers in background
for i in $(seq 1 $SEARCH_WORKERS); do
    search_worker $i &
done

# Progress indicator
end_time=$((START_TIME + DURATION))
while [ $(date +%s) -lt $end_time ]; do
    elapsed=$(($(date +%s) - START_TIME))
    remaining=$((DURATION - elapsed))
    printf "\r${YELLOW}Running... ${elapsed}s elapsed, ${remaining}s remaining${NC}    "
    sleep 2
done

# Wait for all workers to finish
wait

echo -e "\r${GREEN}Test complete!                                    ${NC}"
echo ""

# ── Results ───────────────────────────────────────────────────

TOTAL_INGESTED=0; TOTAL_FAILED_I=0; TOTAL_SEARCHES=0; TOTAL_FAILED_S=0; TOTAL_LATENCY=0

while IFS= read -r line; do
    if [[ $line == worker:* ]]; then
        ing=$(echo "$line" | grep -o 'ingested:[0-9]*' | cut -d: -f2)
        fail=$(echo "$line" | grep -o 'failed:[0-9]*' | cut -d: -f2)
        TOTAL_INGESTED=$((TOTAL_INGESTED + ing))
        TOTAL_FAILED_I=$((TOTAL_FAILED_I + fail))
    elif [[ $line == search_worker:* ]]; then
        srch=$(echo "$line" | grep -o 'searches:[0-9]*' | cut -d: -f2)
        fail=$(echo "$line" | grep -o 'failed:[0-9]*' | cut -d: -f2)
        lat=$(echo "$line" | grep -o 'avg_latency:[0-9]*' | cut -d: -f2)
        TOTAL_SEARCHES=$((TOTAL_SEARCHES + srch))
        TOTAL_FAILED_S=$((TOTAL_FAILED_S + fail))
        TOTAL_LATENCY=$((TOTAL_LATENCY + lat))
    fi
done < /tmp/load_test_results.txt

EVENTS_PER_MIN=$(( (TOTAL_INGESTED * 60) / DURATION ))
AVG_SEARCH_LATENCY=0
if [ $SEARCH_WORKERS -gt 0 ] && [ $TOTAL_SEARCHES -gt 0 ]; then
    AVG_SEARCH_LATENCY=$((TOTAL_LATENCY / SEARCH_WORKERS))
fi

echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                  RESULTS                        ║${NC}"
echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║ INGESTION                                        ║${NC}"
echo -e "${BLUE}║${NC}  Total events ingested : ${GREEN}${TOTAL_INGESTED}${NC}"
echo -e "${BLUE}║${NC}  Events per minute     : ${GREEN}${EVENTS_PER_MIN}${NC}"
echo -e "${BLUE}║${NC}  Failed batches        : ${RED}${TOTAL_FAILED_I}${NC}"
echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║ SEARCH                                           ║${NC}"
echo -e "${BLUE}║${NC}  Total searches        : ${GREEN}${TOTAL_SEARCHES}${NC}"
echo -e "${BLUE}║${NC}  Avg search latency    : ${GREEN}${AVG_SEARCH_LATENCY}ms${NC}"
echo -e "${BLUE}║${NC}  Failed searches       : ${RED}${TOTAL_FAILED_S}${NC}"
echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║ TARGETS                                          ║${NC}"

# Check targets
if [ $EVENTS_PER_MIN -ge 50000 ]; then
    echo -e "${BLUE}║${NC}  50K+ events/min   : ${GREEN}✓ PASS (${EVENTS_PER_MIN}/min)${NC}"
else
    echo -e "${BLUE}║${NC}  50K+ events/min   : ${RED}✗ FAIL (${EVENTS_PER_MIN}/min)${NC}"
fi

if [ $AVG_SEARCH_LATENCY -le 300 ] && [ $AVG_SEARCH_LATENCY -gt 0 ]; then
    echo -e "${BLUE}║${NC}  <300ms search     : ${GREEN}✓ PASS (${AVG_SEARCH_LATENCY}ms avg)${NC}"
else
    echo -e "${BLUE}║${NC}  <300ms search     : ${RED}✗ FAIL (${AVG_SEARCH_LATENCY}ms avg)${NC}"
fi

echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"

rm -f /tmp/load_test_results.txt
