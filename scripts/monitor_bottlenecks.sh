#!/bin/bash
# Real-time bottleneck monitoring script for migration

echo "=========================================="
echo "Real-Time Bottleneck Monitor"
echo "=========================================="
echo ""

# Check if migration is running
if ! pgrep -f "spark-submit.*YugabyteMigrate" > /dev/null; then
    echo "⚠️  Migration is not running"
    echo ""
    echo "To start migration: ./run_250k_migration.sh"
    exit 0
fi

echo "✅ Migration is running"
echo ""

# Monitor containers
echo "=== Container Resource Usage ==="
docker stats --no-stream cassandra yugabyte --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"
echo ""

# Check database connections
echo "=== Database Connections ==="
YUGABYTE_CONNECTIONS=$(docker exec yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore -t -c "SELECT count(*) FROM pg_stat_activity WHERE datname = '\''transaction_datastore'\'';"' 2>&1 | grep -E "^[[:space:]]*[0-9]" | tr -d '[:space:]' || echo "0")
echo "YugabyteDB Active Connections: $YUGABYTE_CONNECTIONS / 300"
echo ""

# Check Spark process
echo "=== Spark Process ==="
SPARK_PID=$(pgrep -f "spark-submit.*YugabyteMigrate")
if [ ! -z "$SPARK_PID" ]; then
    ps -p $SPARK_PID -o pid,pcpu,pmem,rss,vsz,cmd | head -2
    echo ""
    echo "Memory Usage:"
    ps -p $SPARK_PID -o rss= | awk '{printf "  RSS: %.2f GB\n", $1/1024/1024}'
fi
echo ""

# Check latest log entries
echo "=== Recent Migration Activity ==="
LATEST_LOG=$(ls -t migration_250k_*.log 2>/dev/null | head -1)
if [ ! -z "$LATEST_LOG" ]; then
    echo "Latest log: $LATEST_LOG"
    echo ""
    echo "Recent partition completions:"
    tail -20 "$LATEST_LOG" | grep "Partition complete" | tail -5 || echo "  No partition completions yet"
    echo ""
    echo "Recent rate limit logs:"
    tail -20 "$LATEST_LOG" | grep "Rate Limit" | tail -2 || echo "  No rate limit logs"
fi
echo ""

# Check for bottlenecks
echo "=== Bottleneck Indicators ==="

# Check if executor memory is too low
if ps -p $SPARK_PID -o cmd= 2>/dev/null | grep -q "executor-memory 1g"; then
    echo "❌ BOTTLENECK: Executor memory is 1g (too small)"
    echo "   Fix: Add --executor-memory 8G to spark-submit"
fi

# Check if executor cores is 1
if ps -p $SPARK_PID -o cmd= 2>/dev/null | grep -q "executor-cores 1\|executor-cores\"\|\"executor-cores"; then
    echo "❌ BOTTLENECK: Executor cores is 1 (too small)"
    echo "   Fix: Add --executor-cores 4 to spark-submit"
fi

# Check database CPU
CASSANDRA_CPU=$(docker stats --no-stream cassandra --format "{{.CPUPerc}}" | sed 's/%//')
YUGABYTE_CPU=$(docker stats --no-stream yugabyte --format "{{.CPUPerc}}" | sed 's/%//')

if (( $(echo "$CASSANDRA_CPU > 80" | bc -l) )); then
    echo "⚠️  WARNING: Cassandra CPU high: ${CASSANDRA_CPU}%"
fi

if (( $(echo "$YUGABYTE_CPU > 80" | bc -l) )); then
    echo "⚠️  WARNING: YugabyteDB CPU high: ${YUGABYTE_CPU}%"
else
    echo "✅ Database CPU usage normal (Cassandra: ${CASSANDRA_CPU}%, YugabyteDB: ${YUGABYTE_CPU}%)"
fi

echo ""
echo "=========================================="
echo "Monitor complete. Run again to see updates."
echo "=========================================="

