#!/bin/bash

# CDM Connection Monitoring Script
# Monitors connection-related logs and errors in real-time during CDM execution

# Configuration
YUGABYTE_HOST="${YUGABYTE_HOST:-vcausc11udev057.azr.bank-dns.com}"
YUGABYTE_PORT="${YUGABYTE_PORT:-9042}"
LOG_DIR="${LOG_DIR:-migration_logs}"
INTERVAL="${INTERVAL:-5}"  # Check every 5 seconds

echo "=========================================="
echo "CDM Connection Monitor"
echo "=========================================="
echo "YugabyteDB Host: $YUGABYTE_HOST:$YUGABYTE_PORT"
echo "Log Directory: $LOG_DIR"
echo "Check Interval: $INTERVAL seconds"
echo ""
echo "Monitoring CDM connections..."
echo "Press Ctrl+C to stop"
echo ""

# Check if log directory exists
if [ ! -d "$LOG_DIR" ]; then
    echo "Warning: Log directory '$LOG_DIR' does not exist. Creating it..."
    mkdir -p "$LOG_DIR"
fi

ERROR_COUNT=0
LAST_ERROR_COUNT=0

while true; do
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Count connection-related log entries
    CONNECTION_LOGS=$(find "$LOG_DIR" -name "*.log" -type f -exec grep -l "connection\|pool" {} \; 2>/dev/null | wc -l | tr -d ' ')
    
    # Count connection errors
    CONNECTION_ERRORS=$(find "$LOG_DIR" -name "*.log" -type f -exec grep -i "no connection\|pool exhausted\|connection.*timeout\|connection.*failed\|could not.*connection" {} \; 2>/dev/null | wc -l | tr -d ' ')
    
    # Count unique error types
    NO_CONNECTION=$(find "$LOG_DIR" -name "*.log" -type f -exec grep -i "no connection" {} \; 2>/dev/null | wc -l | tr -d ' ')
    POOL_EXHAUSTED=$(find "$LOG_DIR" -name "*.log" -type f -exec grep -i "pool exhausted" {} \; 2>/dev/null | wc -l | tr -d ' ')
    TIMEOUT=$(find "$LOG_DIR" -name "*.log" -type f -exec grep -i "connection.*timeout" {} \; 2>/dev/null | wc -l | tr -d ' ')
    
    # Show status
    echo "[$TIMESTAMP]"
    echo "  Connection Log Files: $CONNECTION_LOGS"
    echo "  Total Connection Errors: $CONNECTION_ERRORS"
    echo "    - No Connection: $NO_CONNECTION"
    echo "    - Pool Exhausted: $POOL_EXHAUSTED"
    echo "    - Timeout: $TIMEOUT"
    
    # Alert if new errors detected
    if [ "$CONNECTION_ERRORS" -gt "$LAST_ERROR_COUNT" ]; then
        NEW_ERRORS=$((CONNECTION_ERRORS - LAST_ERROR_COUNT))
        echo "  ⚠️  ALERT: $NEW_ERRORS new connection error(s) detected!"
        echo "  Recent errors:"
        find "$LOG_DIR" -name "*.log" -type f -exec grep -i "no connection\|pool exhausted\|connection.*timeout" {} \; 2>/dev/null | tail -3 | sed 's/^/    /'
    fi
    
    LAST_ERROR_COUNT=$CONNECTION_ERRORS
    
    # Check for CDM process (optional)
    if pgrep -f "cassandra-data-migrator" > /dev/null; then
        echo "  CDM Process: Running"
    else
        echo "  CDM Process: Not running"
    fi
    
    echo ""
    
    sleep $INTERVAL
done

