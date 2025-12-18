#!/bin/bash
# Monitor CDM Migration Progress

LOG_FILE="migration_100k_background.log"

if [ ! -f "$LOG_FILE" ]; then
    echo "Log file not found: $LOG_FILE"
    exit 1
fi

echo "=== CDM Migration Status Monitor ==="
echo "Press Ctrl+C to stop monitoring"
echo ""

# Function to show current status
show_status() {
    echo "----------------------------------------"
    echo "Time: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "----------------------------------------"
    
    # Check if process is still running
    if pgrep -f "spark-submit.*YugabyteMigrate" > /dev/null; then
        echo "✅ Migration process: RUNNING"
    else
        echo "❌ Migration process: STOPPED"
    fi
    
    echo ""
    echo "Recent Log Activity:"
    tail -15 "$LOG_FILE" | grep -E "(JobCounter|Final|records|ERROR|Successfully|Discovered|Partition)" || tail -5 "$LOG_FILE"
    
    echo ""
    echo "Record Counts (if available):"
    tail -50 "$LOG_FILE" | grep -E "(Final Read|Final Write|Final Error|records written)" | tail -5
    
    echo ""
    echo "Errors (if any):"
    tail -100 "$LOG_FILE" | grep -i "ERROR" | tail -3
    
    echo ""
}

# Show initial status
show_status

# Monitor every 5 seconds
while true; do
    sleep 5
    clear
    show_status
done

