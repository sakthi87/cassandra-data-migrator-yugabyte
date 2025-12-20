#!/bin/bash

# Resource Utilization Monitoring Script
# Monitors Spark, Yugabyte, Cassandra, and System resources during migration

OUTPUT_DIR="resource_monitoring_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUTPUT_DIR"

echo "=========================================="
echo "Resource Utilization Monitoring"
echo "=========================================="
echo "Output Directory: $OUTPUT_DIR"
echo ""

# Function to get process IDs
get_pids() {
    SPARK_PID=$(ps aux | grep -E "spark-submit.*YugabyteMigrate" | grep -v grep | awk '{print $2}' | head -1)
    YUGABYTE_PID=$(docker inspect yugabyte --format '{{.State.Pid}}' 2>/dev/null)
    CASSANDRA_PID=$(docker inspect cassandra --format '{{.State.Pid}}' 2>/dev/null)
    
    echo "Process IDs:"
    echo "  Spark: $SPARK_PID"
    echo "  Yugabyte: $YUGABYTE_PID"
    echo "  Cassandra: $CASSANDRA_PID"
    echo ""
}

# Function to get CPU and Memory for a process
get_process_stats() {
    local pid=$1
    local name=$2
    
    if [ -z "$pid" ] || [ "$pid" = "null" ]; then
        echo "0,0,0"  # CPU%, Memory MB, Threads
        return
    fi
    
    # Get CPU and Memory (macOS)
    local stats=$(ps -p $pid -o %cpu,rss,thcount 2>/dev/null | tail -1)
    if [ -z "$stats" ]; then
        echo "0,0,0"
        return
    fi
    
    local cpu=$(echo $stats | awk '{print $1}')
    local mem_mb=$(echo $stats | awk '{print $2/1024}')
    local threads=$(echo $stats | awk '{print $3}')
    
    echo "$cpu,$mem_mb,$threads"
}

# Function to get Docker container stats
get_docker_stats() {
    local container=$1
    local name=$2
    
    if [ -z "$container" ]; then
        echo "0,0,0,0,0,0"  # CPU%, Mem%, MemMB, NetIn, NetOut, BlockIO
        return
    fi
    
    # Get container stats
    local stats=$(docker stats --no-stream --format "{{.CPUPerc}},{{.MemPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}" $container 2>/dev/null)
    
    if [ -z "$stats" ]; then
        echo "0,0,0,0,0,0"
        return
    fi
    
    # Parse stats
    local cpu=$(echo $stats | cut -d',' -f1 | sed 's/%//')
    local mem_perc=$(echo $stats | cut -d',' -f2 | sed 's/%//')
    local mem_usage=$(echo $stats | cut -d',' -f3 | awk '{print $1}' | sed 's/[^0-9.]//g')
    local net_io=$(echo $stats | cut -d',' -f4)
    local block_io=$(echo $stats | cut -d',' -f5)
    
    # Convert memory to MB if needed
    local mem_mb=$mem_usage
    if [[ $mem_usage == *"GiB"* ]] || [[ $mem_usage == *"GB"* ]]; then
        mem_mb=$(echo "$mem_usage * 1024" | bc 2>/dev/null || echo "0")
    elif [[ $mem_usage == *"KiB"* ]] || [[ $mem_usage == *"KB"* ]]; then
        mem_mb=$(echo "$mem_usage / 1024" | bc 2>/dev/null || echo "0")
    fi
    
    echo "$cpu,$mem_perc,$mem_mb,$net_io,$block_io"
}

# Function to get system-wide stats
get_system_stats() {
    # CPU usage (macOS)
    local cpu_usage=$(top -l 1 | grep "CPU usage" | awk '{print $3}' | sed 's/%//')
    
    # Memory stats (macOS)
    local mem_stats=$(vm_stat | grep -E "Pages free|Pages active|Pages inactive|Pages speculative|Pages wired down")
    local free_pages=$(echo "$mem_stats" | grep "Pages free" | awk '{print $3}' | sed 's/\.//')
    local active_pages=$(echo "$mem_stats" | grep "Pages active" | awk '{print $3}' | sed 's/\.//')
    local inactive_pages=$(echo "$mem_stats" | grep "Pages inactive" | awk '{print $3}' | sed 's/\.//')
    
    # Calculate memory usage percentage (approximate)
    local total_pages=$((free_pages + active_pages + inactive_pages))
    local used_pages=$((active_pages + inactive_pages))
    local mem_percent=0
    if [ $total_pages -gt 0 ]; then
        mem_percent=$((used_pages * 100 / total_pages))
    fi
    
    # Get load average
    local load_avg=$(sysctl -n vm.loadavg | awk '{print $2}')
    
    echo "$cpu_usage,$mem_percent,$load_avg"
}

# Function to get network stats
get_network_stats() {
    # Get network I/O (macOS)
    local netstat=$(netstat -ib | grep -E "en0|en1" | head -1)
    local bytes_in=$(echo $netstat | awk '{print $7}')
    local bytes_out=$(echo $netstat | awk '{print $10}')
    
    echo "$bytes_in,$bytes_out"
}

# Initialize CSV files with headers
echo "timestamp,cpu_percent,mem_mb,threads" > "$OUTPUT_DIR/spark_stats.csv"
echo "timestamp,cpu_percent,mem_percent,mem_mb,net_io,block_io" > "$OUTPUT_DIR/yugabyte_stats.csv"
echo "timestamp,cpu_percent,mem_percent,mem_mb,net_io,block_io" > "$OUTPUT_DIR/cassandra_stats.csv"
echo "timestamp,cpu_percent,mem_percent,load_avg" > "$OUTPUT_DIR/system_stats.csv"
echo "timestamp,bytes_in,bytes_out" > "$OUTPUT_DIR/network_stats.csv"

echo "Starting monitoring (sampling every 2 seconds)..."
echo "Press Ctrl+C to stop monitoring"
echo ""

# Get initial PIDs
get_pids

# Monitoring loop
MONITORING=true
SAMPLE_COUNT=0

trap 'MONITORING=false' INT TERM

while [ "$MONITORING" = true ]; do
    TIMESTAMP=$(date +%Y-%m-%d\ %H:%M:%S)
    
    # Get Spark stats
    SPARK_STATS=$(get_process_stats "$SPARK_PID" "Spark")
    echo "$TIMESTAMP,$SPARK_STATS" >> "$OUTPUT_DIR/spark_stats.csv"
    
    # Get Yugabyte stats
    YUGABYTE_STATS=$(get_docker_stats "yugabyte" "Yugabyte")
    echo "$TIMESTAMP,$YUGABYTE_STATS" >> "$OUTPUT_DIR/yugabyte_stats.csv"
    
    # Get Cassandra stats
    CASSANDRA_STATS=$(get_docker_stats "cassandra" "Cassandra")
    echo "$TIMESTAMP,$CASSANDRA_STATS" >> "$OUTPUT_DIR/cassandra_stats.csv"
    
    # Get system stats
    SYSTEM_STATS=$(get_system_stats)
    echo "$TIMESTAMP,$SYSTEM_STATS" >> "$OUTPUT_DIR/system_stats.csv"
    
    # Get network stats
    NETWORK_STATS=$(get_network_stats)
    echo "$TIMESTAMP,$NETWORK_STATS" >> "$OUTPUT_DIR/network_stats.csv"
    
    # Refresh PIDs (in case Spark restarts)
    if [ $((SAMPLE_COUNT % 10)) -eq 0 ]; then
        get_pids > /dev/null 2>&1
    fi
    
    SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
    
    # Display current stats
    if [ $((SAMPLE_COUNT % 5)) -eq 0 ]; then
        echo "[$TIMESTAMP] Sample #$SAMPLE_COUNT"
        echo "  Spark: CPU=$(echo $SPARK_STATS | cut -d',' -f1)%, Mem=$(echo $SPARK_STATS | cut -d',' -f2)MB"
        echo "  Yugabyte: CPU=$(echo $YUGABYTE_STATS | cut -d',' -f1)%, Mem=$(echo $YUGABYTE_STATS | cut -d',' -f3)MB"
        echo "  Cassandra: CPU=$(echo $CASSANDRA_STATS | cut -d',' -f1)%, Mem=$(echo $CASSANDRA_STATS | cut -d',' -f3)MB"
        echo "  System: CPU=$(echo $SYSTEM_STATS | cut -d',' -f1)%, Mem=$(echo $SYSTEM_STATS | cut -d',' -f2)%"
        echo ""
    fi
    
    sleep 2
done

echo ""
echo "Monitoring stopped. Generating summary..."
echo ""

# Generate summary report
python3 << 'PYTHON_EOF'
import csv
import os
import glob
from statistics import mean, max as max_stat

output_dir = sorted(glob.glob('resource_monitoring_*'), reverse=True)[0] if glob.glob('resource_monitoring_*') else None

if not output_dir:
    print("No monitoring data found")
    exit(1)

print("=" * 80)
print("RESOURCE UTILIZATION SUMMARY")
print("=" * 80)
print(f"Monitoring Directory: {output_dir}\n")

# Read Spark stats
spark_cpu = []
spark_mem = []
spark_threads = []

try:
    with open(f'{output_dir}/spark_stats.csv', 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['cpu_percent'] and row['cpu_percent'] != '0':
                spark_cpu.append(float(row['cpu_percent']))
            if row['mem_mb'] and row['mem_mb'] != '0':
                spark_mem.append(float(row['mem_mb']))
            if row['threads'] and row['threads'] != '0':
                spark_threads.append(int(row['threads']))
except Exception as e:
    print(f"Error reading Spark stats: {e}")

# Read Yugabyte stats
yugabyte_cpu = []
yugabyte_mem = []
yugabyte_mem_mb = []

try:
    with open(f'{output_dir}/yugabyte_stats.csv', 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['cpu_percent'] and row['cpu_percent'] != '0':
                yugabyte_cpu.append(float(row['cpu_percent']))
            if row['mem_percent'] and row['mem_percent'] != '0':
                yugabyte_mem.append(float(row['mem_percent']))
            if row['mem_mb'] and row['mem_mb'] != '0':
                try:
                    yugabyte_mem_mb.append(float(row['mem_mb']))
                except:
                    pass
except Exception as e:
    print(f"Error reading Yugabyte stats: {e}")

# Read Cassandra stats
cassandra_cpu = []
cassandra_mem = []
cassandra_mem_mb = []

try:
    with open(f'{output_dir}/cassandra_stats.csv', 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['cpu_percent'] and row['cpu_percent'] != '0':
                cassandra_cpu.append(float(row['cpu_percent']))
            if row['mem_percent'] and row['mem_percent'] != '0':
                cassandra_mem.append(float(row['mem_percent']))
            if row['mem_mb'] and row['mem_mb'] != '0':
                try:
                    cassandra_mem_mb.append(float(row['mem_mb']))
                except:
                    pass
except Exception as e:
    print(f"Error reading Cassandra stats: {e}")

# Read System stats
system_cpu = []
system_mem = []
system_load = []

try:
    with open(f'{output_dir}/system_stats.csv', 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['cpu_percent'] and row['cpu_percent'] != '0':
                system_cpu.append(float(row['cpu_percent']))
            if row['mem_percent'] and row['mem_percent'] != '0':
                system_mem.append(float(row['mem_percent']))
            if row['load_avg']:
                try:
                    system_load.append(float(row['load_avg']))
                except:
                    pass
except Exception as e:
    print(f"Error reading System stats: {e}")

# Print summary
print("SPARK PROCESS:")
if spark_cpu:
    print(f"  CPU: Avg={mean(spark_cpu):.1f}%, Max={max_stat(spark_cpu):.1f}%")
if spark_mem:
    print(f"  Memory: Avg={mean(spark_mem):.1f}MB, Max={max_stat(spark_mem):.1f}MB")
if spark_threads:
    print(f"  Threads: Avg={mean(spark_threads):.0f}, Max={max_stat(spark_threads)}")
print()

print("YUGABYTE CONTAINER:")
if yugabyte_cpu:
    print(f"  CPU: Avg={mean(yugabyte_cpu):.1f}%, Max={max_stat(yugabyte_cpu):.1f}%")
if yugabyte_mem:
    print(f"  Memory: Avg={mean(yugabyte_mem):.1f}%, Max={max_stat(yugabyte_mem):.1f}%")
if yugabyte_mem_mb:
    print(f"  Memory: Avg={mean(yugabyte_mem_mb):.1f}MB, Max={max_stat(yugabyte_mem_mb):.1f}MB")
print()

print("CASSANDRA CONTAINER:")
if cassandra_cpu:
    print(f"  CPU: Avg={mean(cassandra_cpu):.1f}%, Max={max_stat(cassandra_cpu):.1f}%")
if cassandra_mem:
    print(f"  Memory: Avg={mean(cassandra_mem):.1f}%, Max={max_stat(cassandra_mem):.1f}%")
if cassandra_mem_mb:
    print(f"  Memory: Avg={mean(cassandra_mem_mb):.1f}MB, Max={max_stat(cassandra_mem_mb):.1f}MB")
print()

print("SYSTEM RESOURCES:")
if system_cpu:
    print(f"  CPU: Avg={mean(system_cpu):.1f}%, Max={max_stat(system_cpu):.1f}%")
if system_mem:
    print(f"  Memory: Avg={mean(system_mem):.1f}%, Max={max_stat(system_mem):.1f}%")
if system_load:
    print(f"  Load Average: Avg={mean(system_load):.2f}, Max={max_stat(system_load):.2f}")
print()

# Identify bottlenecks
print("BOTTLENECK ANALYSIS:")
print("-" * 80)

bottlenecks = []

if spark_cpu and max_stat(spark_cpu) > 80:
    bottlenecks.append("Spark CPU utilization is high (>80%)")
if spark_mem and max_stat(spark_mem) > 7000:
    bottlenecks.append("Spark Memory usage is high (>7GB)")

if yugabyte_cpu and max_stat(yugabyte_cpu) > 80:
    bottlenecks.append("Yugabyte CPU utilization is high (>80%) - WRITE BOTTLENECK")
if yugabyte_mem and max_stat(yugabyte_mem) > 90:
    bottlenecks.append("Yugabyte Memory usage is high (>90%) - WRITE BOTTLENECK")

if cassandra_cpu and max_stat(cassandra_cpu) > 80:
    bottlenecks.append("Cassandra CPU utilization is high (>80%) - READ BOTTLENECK")
if cassandra_mem and max_stat(cassandra_mem) > 90:
    bottlenecks.append("Cassandra Memory usage is high (>90%) - READ BOTTLENECK")

if system_cpu and max_stat(system_cpu) > 90:
    bottlenecks.append("System CPU is saturated (>90%) - SYSTEM RESOURCE BOTTLENECK")
if system_mem and max_stat(system_mem) > 90:
    bottlenecks.append("System Memory is saturated (>90%) - SYSTEM RESOURCE BOTTLENECK")

if bottlenecks:
    for i, bottleneck in enumerate(bottlenecks, 1):
        print(f"{i}. {bottleneck}")
else:
    print("No obvious bottlenecks detected. System resources appear adequate.")

print()
print("=" * 80)
print(f"Detailed stats saved in: {output_dir}/")
print("=" * 80)
PYTHON_EOF

echo ""
echo "Monitoring complete!"

