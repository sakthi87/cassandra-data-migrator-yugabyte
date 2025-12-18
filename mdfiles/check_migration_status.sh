#!/bin/bash
# Quick status check script for CDM migration

LOG_FILE="migration_100k_background.log"

echo "=== CDM Migration Status ==="
echo ""

# Check if process is running
if pgrep -f "spark-submit.*YugabyteMigrate" > /dev/null; then
    echo "✅ Status: RUNNING"
    PID=$(pgrep -f "spark-submit.*YugabyteMigrate")
    echo "   PID: $PID"
else
    echo "✅ Status: COMPLETED"
fi

echo ""

# Check record counts
echo "=== Record Counts ==="
CASSANDRA_COUNT=$(docker exec -i cassandra cqlsh localhost 9042 -e "SELECT COUNT(*) FROM transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr;" 2>/dev/null | grep -E "^ [0-9]" | tr -d ' ' || echo "0")
YUGABYTE_COUNT=$(docker exec -i yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore -t -c "SELECT COUNT(*) FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr;"' 2>/dev/null | grep -E "^[0-9]" | tr -d ' ' || echo "0")

echo "Cassandra (source): $CASSANDRA_COUNT records"
echo "YugabyteDB (target): $YUGABYTE_COUNT records"
echo ""

if [ "$CASSANDRA_COUNT" != "0" ] && [ "$YUGABYTE_COUNT" != "0" ]; then
    PERCENTAGE=$((YUGABYTE_COUNT * 100 / CASSANDRA_COUNT))
    echo "Progress: $PERCENTAGE% ($YUGABYTE_COUNT / $CASSANDRA_COUNT)"
fi

echo ""

# Show recent log activity
if [ -f "$LOG_FILE" ]; then
    echo "=== Recent Activity ==="
    tail -10 "$LOG_FILE" | grep -E "(JobCounter|Final|Partition|ERROR|records)" | tail -5 || tail -3 "$LOG_FILE"
fi

echo ""
echo "=== Performance Metrics (if available) ==="
if [ -f "$LOG_FILE" ]; then
    tail -50 "$LOG_FILE" | grep -E "(Final Read|Final Write|records/sec|IOPS)" | tail -5
fi

echo ""
echo "To see full log: tail -f $LOG_FILE"
echo "To monitor continuously: ./monitor_migration.sh"

