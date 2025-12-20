#!/bin/bash

# Run multiple migrations to get average performance
# Usage: ./run_multiple_migrations.sh [number_of_runs]

NUM_RUNS=${1:-4}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Running $NUM_RUNS Migrations for Average Performance"
echo "=========================================="
echo ""

RESULTS_DIR="migration_batch_results_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$RESULTS_DIR"

declare -a DURATIONS
declare -a THROUGHPUTS
declare -a RECORDS
declare -a ERRORS

for i in $(seq 1 $NUM_RUNS); do
    echo "=========================================="
    echo "Migration Run $i of $NUM_RUNS"
    echo "=========================================="
    echo ""
    
    # Truncate Yugabyte table
    echo "Truncating Yugabyte table..."
    docker exec yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore -c "TRUNCATE TABLE dda_pstd_fincl_txn_cnsmr_by_accntnbr;"' 2>&1 > /dev/null
    echo "✅ Table truncated"
    echo ""
    
    # Run migration
    echo "Starting migration..."
    LOG_FILE="$RESULTS_DIR/migration_run_${i}_$(date +%Y%m%d_%H%M%S).log"
    ./run_250k_migration.sh 2>&1 | tee "$LOG_FILE"
    EXIT_CODE=$?
    
    echo ""
    echo "Analyzing run $i..."
    
    # Extract metrics
    python3 << PYTHON_EOF
import re
from datetime import datetime

log_file = "$LOG_FILE"
start_time = None
end_time = None
total_records = 0
errors = 0
partitions_complete = 0

try:
    with open(log_file, 'r') as f:
        for line in f:
            if 'Processing min:' in line and start_time is None:
                match = re.search(r'(\d{2}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})', line)
                if match:
                    start_time = match.group(1)
            
            if 'Partition complete' in line:
                partitions_complete += 1
            
            if 'Final Write Record Count:' in line:
                match = re.search(r'Final Write Record Count: (\d+)', line)
                if match:
                    total_records = int(match.group(1))
            
            if 'ERROR' in line or 'Exception' in line:
                errors += 1
            
            match = re.search(r'(\d{2}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})', line)
            if match:
                end_time = match.group(1)
    
    if start_time and end_time and partitions_complete >= 40:
        start = datetime.strptime(start_time, '%y/%m/%d %H:%M:%S')
        end = datetime.strptime(end_time, '%y/%m/%d %H:%M:%S')
        duration = (end - start).total_seconds()
        throughput = total_records / duration if duration > 0 else 0
        
        print(f"DURATION={duration:.1f}")
        print(f"THROUGHPUT={throughput:.0f}")
        print(f"RECORDS={total_records}")
        print(f"ERRORS={errors}")
        print(f"PARTITIONS={partitions_complete}")
        print(f"STATUS=COMPLETE")
    else:
        print(f"STATUS=INCOMPLETE")
        print(f"PARTITIONS={partitions_complete}/40")
except Exception as e:
    print(f"STATUS=ERROR")
    print(f"ERROR_MSG={str(e)}")
PYTHON_EOF
    
    # Capture results
    DURATION=$(grep "^DURATION=" "$RESULTS_DIR/run_${i}_metrics.txt" 2>/dev/null | cut -d'=' -f2 || echo "0")
    THROUGHPUT=$(grep "^THROUGHPUT=" "$RESULTS_DIR/run_${i}_metrics.txt" 2>/dev/null | cut -d'=' -f2 || echo "0")
    RECORD_COUNT=$(grep "^RECORDS=" "$RESULTS_DIR/run_${i}_metrics.txt" 2>/dev/null | cut -d'=' -f2 || echo "0")
    ERROR_COUNT=$(grep "^ERRORS=" "$RESULTS_DIR/run_${i}_metrics.txt" 2>/dev/null | cut -d'=' -f2 || echo "0")
    
    # Save metrics
    python3 << PYTHON_EOF > "$RESULTS_DIR/run_${i}_metrics.txt"
import re
from datetime import datetime

log_file = "$LOG_FILE"
start_time = None
end_time = None
total_records = 0
errors = 0
partitions_complete = 0

try:
    with open(log_file, 'r') as f:
        for line in f:
            if 'Processing min:' in line and start_time is None:
                match = re.search(r'(\d{2}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})', line)
                if match:
                    start_time = match.group(1)
            
            if 'Partition complete' in line:
                partitions_complete += 1
            
            if 'Final Write Record Count:' in line:
                match = re.search(r'Final Write Record Count: (\d+)', line)
                if match:
                    total_records = int(match.group(1))
            
            if 'ERROR' in line or 'Exception' in line:
                errors += 1
            
            match = re.search(r'(\d{2}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})', line)
            if match:
                end_time = match.group(1)
    
    if start_time and end_time and partitions_complete >= 40:
        start = datetime.strptime(start_time, '%y/%m/%d %H:%M:%S')
        end = datetime.strptime(end_time, '%y/%m/%d %H:%M:%S')
        duration = (end - start).total_seconds()
        throughput = total_records / duration if duration > 0 else 0
        
        print(f"DURATION={duration:.1f}")
        print(f"THROUGHPUT={throughput:.0f}")
        print(f"RECORDS={total_records}")
        print(f"ERRORS={errors}")
        print(f"PARTITIONS={partitions_complete}")
        print(f"STATUS=COMPLETE")
    else:
        print(f"STATUS=INCOMPLETE")
        print(f"PARTITIONS={partitions_complete}/40")
except Exception as e:
    print(f"STATUS=ERROR")
    print(f"ERROR_MSG={str(e)}")
PYTHON_EOF
    
    DURATION=$(grep "^DURATION=" "$RESULTS_DIR/run_${i}_metrics.txt" 2>/dev/null | cut -d'=' -f2 || echo "0")
    THROUGHPUT=$(grep "^THROUGHPUT=" "$RESULTS_DIR/run_${i}_metrics.txt" 2>/dev/null | cut -d'=' -f2 || echo "0")
    RECORD_COUNT=$(grep "^RECORDS=" "$RESULTS_DIR/run_${i}_metrics.txt" 2>/dev/null | cut -d'=' -f2 || echo "0")
    ERROR_COUNT=$(grep "^ERRORS=" "$RESULTS_DIR/run_${i}_metrics.txt" 2>/dev/null | cut -d'=' -f2 || echo "0")
    
    if [ "$DURATION" != "0" ] && [ "$THROUGHPUT" != "0" ]; then
        DURATIONS+=($DURATION)
        THROUGHPUTS+=($THROUGHPUT)
        RECORDS+=($RECORD_COUNT)
        ERRORS+=($ERROR_COUNT)
        
        echo "Run $i Results:"
        echo "  Duration: ${DURATION}s"
        echo "  Throughput: ${THROUGHPUT} IOPS ($(echo "scale=2; $THROUGHPUT/1000" | bc)K)"
        echo "  Records: ${RECORD_COUNT}"
        echo "  Errors: ${ERROR_COUNT}"
        echo ""
    else
        echo "⚠️  Run $i: Could not extract metrics"
        echo ""
    fi
    
    # Wait between runs (except after last run)
    if [ $i -lt $NUM_RUNS ]; then
        echo "Waiting 30 seconds before next run..."
        sleep 30
        echo ""
    fi
done

echo "=========================================="
echo "Performance Summary"
echo "=========================================="
echo ""

# Calculate statistics
python3 << PYTHON_EOF
import sys

durations = [${DURATIONS[*]}]
throughputs = [${THROUGHPUTS[*]}]
records = [${RECORDS[*]}]
errors = [${ERRORS[*]}]

if len(durations) > 0 and len(throughputs) > 0:
    avg_duration = sum(durations) / len(durations)
    avg_throughput = sum(throughputs) / len(throughputs)
    min_throughput = min(throughputs)
    max_throughput = max(throughputs)
    
    # Calculate standard deviation
    if len(throughputs) > 1:
        variance = sum((x - avg_throughput) ** 2 for x in throughputs) / len(throughputs)
        std_dev = variance ** 0.5
    else:
        std_dev = 0
    
    print(f"Total Runs: {len(durations)}")
    print(f"")
    print(f"Duration:")
    print(f"  Average: {avg_duration:.1f}s ({avg_duration/60:.1f} min)")
    print(f"  Min: {min(durations):.1f}s")
    print(f"  Max: {max(durations):.1f}s")
    print(f"")
    print(f"Throughput (IOPS):")
    print(f"  Average: {avg_throughput:,.0f} IOPS ({avg_throughput/1000:.2f}K)")
    print(f"  Minimum: {min_throughput:,.0f} IOPS ({min_throughput/1000:.2f}K)")
    print(f"  Maximum: {max_throughput:,.0f} IOPS ({max_throughput/1000:.2f}K)")
    print(f"  Std Dev: {std_dev:,.0f} IOPS ({std_dev/1000:.2f}K)")
    print(f"")
    print(f"Records:")
    print(f"  Total: {sum(records):,}")
    print(f"  Average per run: {sum(records)/len(records):,.0f}")
    print(f"")
    print(f"Errors:")
    print(f"  Total: {sum(errors)}")
    print(f"  Average per run: {sum(errors)/len(errors):.0f}")
    print(f"")
    
    # Compare with previous best
    previous_best = 3049
    improvement = ((avg_throughput - previous_best) / previous_best) * 100
    
    print(f"Comparison with Previous Best (3,049 IOPS):")
    if improvement > 0:
        print(f"  ✅ IMPROVEMENT: +{improvement:.1f}% ({avg_throughput - previous_best:,.0f} IOPS)")
    elif improvement > -5:
        print(f"  ⚠️  Similar: {improvement:.1f}% ({avg_throughput - previous_best:,.0f} IOPS)")
        print(f"     Within normal variance")
    else:
        print(f"  ⚠️  Lower: {improvement:.1f}% ({avg_throughput - previous_best:,.0f} IOPS)")
        print(f"     May need further investigation")
    
    # Individual run results
    print(f"")
    print(f"Individual Run Results:")
    for i, (dur, thr) in enumerate(zip(durations, throughputs), 1):
        print(f"  Run {i}: {thr:,.0f} IOPS ({dur:.1f}s)")
else:
    print("No valid results to analyze")
PYTHON_EOF

echo ""
echo "=========================================="
echo "Results saved in: $RESULTS_DIR"
echo "=========================================="

