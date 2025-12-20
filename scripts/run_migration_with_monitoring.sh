#!/bin/bash

# Run migration with comprehensive resource monitoring

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Migration with Resource Monitoring"
echo "=========================================="
echo ""

# Check if migration is already running
if pgrep -f "spark-submit.*YugabyteMigrate" > /dev/null; then
    echo "⚠️  Migration is already running. Please stop it first."
    exit 1
fi

# Start monitoring in background
echo "Starting resource monitoring..."
./monitor_resource_utilization.sh > /dev/null 2>&1 &
MONITOR_PID=$!
echo "Monitoring started with PID: $MONITOR_PID"
echo ""

# Wait a moment for monitoring to initialize
sleep 3

# Start migration
echo "Starting migration..."
./run_250k_migration.sh 2>&1 | tee migration_with_monitoring_$(date +%Y%m%d_%H%M%S).log &
MIGRATION_PID=$!
echo "Migration started with PID: $MIGRATION_PID"
echo ""

# Wait for migration to complete
echo "Waiting for migration to complete..."
wait $MIGRATION_PID
MIGRATION_EXIT=$?

# Stop monitoring
echo ""
echo "Stopping monitoring..."
kill $MONITOR_PID 2>/dev/null
sleep 2

# Generate analysis
echo ""
echo "=========================================="
echo "Generating Resource Utilization Analysis"
echo "=========================================="
echo ""

# Find the latest monitoring directory
LATEST_MONITOR_DIR=$(ls -td resource_monitoring_* 2>/dev/null | head -1)

if [ -n "$LATEST_MONITOR_DIR" ]; then
    echo "Analyzing monitoring data from: $LATEST_MONITOR_DIR"
    python3 << 'PYTHON_EOF'
import csv
import glob
from statistics import mean, median

monitor_dir = sorted(glob.glob('resource_monitoring_*'), reverse=True)[0] if glob.glob('resource_monitoring_*') else None

if not monitor_dir:
    print("No monitoring data found")
    exit(1)

print("=" * 80)
print("COMPREHENSIVE RESOURCE UTILIZATION ANALYSIS")
print("=" * 80)
print(f"Monitoring Directory: {monitor_dir}\n")

# Read all stats
def read_csv(filepath, required_cols):
    data = {col: [] for col in required_cols}
    try:
        with open(filepath, 'r') as f:
            reader = csv.DictReader(f)
            for row in reader:
                for col in required_cols:
                    try:
                        val = float(row[col]) if row[col] and row[col] != '0' else None
                        if val is not None:
                            data[col].append(val)
                    except:
                        pass
    except Exception as e:
        print(f"Error reading {filepath}: {e}")
    return data

# Spark stats
spark_data = read_csv(f'{monitor_dir}/spark_stats.csv', ['cpu_percent', 'mem_mb', 'threads'])

# Yugabyte stats
yugabyte_data = read_csv(f'{monitor_dir}/yugabyte_stats.csv', ['cpu_percent', 'mem_percent', 'mem_mb'])

# Cassandra stats
cassandra_data = read_csv(f'{monitor_dir}/cassandra_stats.csv', ['cpu_percent', 'mem_percent', 'mem_mb'])

# System stats
system_data = read_csv(f'{monitor_dir}/system_stats.csv', ['cpu_percent', 'mem_percent', 'load_avg'])

# Print detailed stats
print("1. SPARK PROCESS RESOURCES")
print("-" * 80)
if spark_data['cpu_percent']:
    print(f"   CPU Usage:")
    print(f"     Average: {mean(spark_data['cpu_percent']):.1f}%")
    print(f"     Maximum: {max(spark_data['cpu_percent']):.1f}%")
    print(f"     Median:  {median(spark_data['cpu_percent']):.1f}%")
if spark_data['mem_mb']:
    print(f"   Memory Usage:")
    print(f"     Average: {mean(spark_data['mem_mb']):.1f} MB")
    print(f"     Maximum: {max(spark_data['mem_mb']):.1f} MB")
    print(f"     Median:  {median(spark_data['mem_mb']):.1f} MB")
if spark_data['threads']:
    print(f"   Threads:")
    print(f"     Average: {mean(spark_data['threads']):.0f}")
    print(f"     Maximum: {max(spark_data['threads'])}")
print()

print("2. YUGABYTE CONTAINER RESOURCES (WRITE OPERATIONS)")
print("-" * 80)
if yugabyte_data['cpu_percent']:
    print(f"   CPU Usage:")
    print(f"     Average: {mean(yugabyte_data['cpu_percent']):.1f}%")
    print(f"     Maximum: {max(yugabyte_data['cpu_percent']):.1f}%")
    print(f"     Median:  {median(yugabyte_data['cpu_percent']):.1f}%")
if yugabyte_data['mem_percent']:
    print(f"   Memory Usage (%):")
    print(f"     Average: {mean(yugabyte_data['mem_percent']):.1f}%")
    print(f"     Maximum: {max(yugabyte_data['mem_percent']):.1f}%")
if yugabyte_data['mem_mb']:
    print(f"   Memory Usage (MB):")
    print(f"     Average: {mean(yugabyte_data['mem_mb']):.1f} MB")
    print(f"     Maximum: {max(yugabyte_data['mem_mb']):.1f} MB")
print()

print("3. CASSANDRA CONTAINER RESOURCES (READ OPERATIONS)")
print("-" * 80)
if cassandra_data['cpu_percent']:
    print(f"   CPU Usage:")
    print(f"     Average: {mean(cassandra_data['cpu_percent']):.1f}%")
    print(f"     Maximum: {max(cassandra_data['cpu_percent']):.1f}%")
    print(f"     Median:  {median(cassandra_data['cpu_percent']):.1f}%")
if cassandra_data['mem_percent']:
    print(f"   Memory Usage (%):")
    print(f"     Average: {mean(cassandra_data['mem_percent']):.1f}%")
    print(f"     Maximum: {max(cassandra_data['mem_percent']):.1f}%")
if cassandra_data['mem_mb']:
    print(f"   Memory Usage (MB):")
    print(f"     Average: {mean(cassandra_data['mem_mb']):.1f} MB")
    print(f"     Maximum: {max(cassandra_data['mem_mb']):.1f} MB")
print()

print("4. SYSTEM RESOURCES (MAC)")
print("-" * 80)
if system_data['cpu_percent']:
    print(f"   CPU Usage:")
    print(f"     Average: {mean(system_data['cpu_percent']):.1f}%")
    print(f"     Maximum: {max(system_data['cpu_percent']):.1f}%")
    print(f"     Median:  {median(system_data['cpu_percent']):.1f}%")
if system_data['mem_percent']:
    print(f"   Memory Usage:")
    print(f"     Average: {mean(system_data['mem_percent']):.1f}%")
    print(f"     Maximum: {max(system_data['mem_percent']):.1f}%")
if system_data['load_avg']:
    print(f"   Load Average:")
    print(f"     Average: {mean(system_data['load_avg']):.2f}")
    print(f"     Maximum: {max(system_data['load_avg']):.2f}")
print()

# Bottleneck Analysis
print("=" * 80)
print("BOTTLENECK IDENTIFICATION")
print("=" * 80)
print()

bottlenecks = []
recommendations = []

# Check Spark
if spark_data['cpu_percent']:
    max_spark_cpu = max(spark_data['cpu_percent'])
    if max_spark_cpu > 90:
        bottlenecks.append(("Spark CPU", "CRITICAL", f"{max_spark_cpu:.1f}%", "Spark processing bottleneck"))
        recommendations.append("Increase Spark executor cores or reduce parallelism")
    elif max_spark_cpu > 70:
        bottlenecks.append(("Spark CPU", "HIGH", f"{max_spark_cpu:.1f}%", "Spark processing may be limiting"))

if spark_data['mem_mb']:
    max_spark_mem = max(spark_data['mem_mb'])
    if max_spark_mem > 8000:
        bottlenecks.append(("Spark Memory", "CRITICAL", f"{max_spark_mem:.0f}MB", "Spark memory bottleneck"))
        recommendations.append("Increase Spark driver/executor memory")

# Check Yugabyte (Write)
if yugabyte_data['cpu_percent']:
    max_yb_cpu = max(yugabyte_data['cpu_percent'])
    if max_yb_cpu > 90:
        bottlenecks.append(("Yugabyte CPU", "CRITICAL", f"{max_yb_cpu:.1f}%", "WRITE BOTTLENECK - Yugabyte write operations"))
        recommendations.append("Yugabyte is CPU-bound. Consider: more Yugabyte nodes, faster CPU, or reduce write load")
    elif max_yb_cpu > 70:
        bottlenecks.append(("Yugabyte CPU", "HIGH", f"{max_yb_cpu:.1f}%", "WRITE BOTTLENECK - Yugabyte write operations"))

if yugabyte_data['mem_percent']:
    max_yb_mem = max(yugabyte_data['mem_percent'])
    if max_yb_mem > 95:
        bottlenecks.append(("Yugabyte Memory", "CRITICAL", f"{max_yb_mem:.1f}%", "WRITE BOTTLENECK - Yugabyte memory"))
        recommendations.append("Increase Yugabyte container memory allocation")

# Check Cassandra (Read)
if cassandra_data['cpu_percent']:
    max_cass_cpu = max(cassandra_data['cpu_percent'])
    if max_cass_cpu > 90:
        bottlenecks.append(("Cassandra CPU", "CRITICAL", f"{max_cass_cpu:.1f}%", "READ BOTTLENECK - Cassandra read operations"))
        recommendations.append("Cassandra is CPU-bound. Consider: more Cassandra nodes, faster CPU, or reduce read parallelism")
    elif max_cass_cpu > 70:
        bottlenecks.append(("Cassandra CPU", "HIGH", f"{max_cass_cpu:.1f}%", "READ BOTTLENECK - Cassandra read operations"))

if cassandra_data['mem_percent']:
    max_cass_mem = max(cassandra_data['mem_percent'])
    if max_cass_mem > 95:
        bottlenecks.append(("Cassandra Memory", "CRITICAL", f"{max_cass_mem:.1f}%", "READ BOTTLENECK - Cassandra memory"))
        recommendations.append("Increase Cassandra container memory allocation")

# Check System
if system_data['cpu_percent']:
    max_sys_cpu = max(system_data['cpu_percent'])
    if max_sys_cpu > 95:
        bottlenecks.append(("System CPU", "CRITICAL", f"{max_sys_cpu:.1f}%", "SYSTEM RESOURCE BOTTLENECK - Mac CPU saturated"))
        recommendations.append("System CPU is the bottleneck. Need: More CPU cores or faster CPU")
    elif max_sys_cpu > 85:
        bottlenecks.append(("System CPU", "HIGH", f"{max_sys_cpu:.1f}%", "SYSTEM RESOURCE BOTTLENECK - Mac CPU high"))

if system_data['mem_percent']:
    max_sys_mem = max(system_data['mem_percent'])
    if max_sys_mem > 95:
        bottlenecks.append(("System Memory", "CRITICAL", f"{max_sys_mem:.1f}%", "SYSTEM RESOURCE BOTTLENECK - Mac memory saturated"))
        recommendations.append("System memory is the bottleneck. Need: More RAM")

if bottlenecks:
    print("IDENTIFIED BOTTLENECKS:")
    print()
    for i, (component, severity, value, description) in enumerate(bottlenecks, 1):
        marker = "🔴" if severity == "CRITICAL" else "🟡"
        print(f"{marker} {i}. {component}: {value} ({severity})")
        print(f"   → {description}")
    print()
    print("RECOMMENDATIONS:")
    for i, rec in enumerate(set(recommendations), 1):
        print(f"   {i}. {rec}")
else:
    print("✅ No critical bottlenecks detected.")
    print("   System resources appear adequate for current load.")
    print("   Performance may be limited by:")
    print("   - Network latency")
    print("   - Database internal operations")
    print("   - Configuration tuning opportunities")

print()
print("=" * 80)
print("RESOURCE REQUIREMENTS FOR HIGHER THROUGHPUT")
print("=" * 80)
print()

# Calculate resource scaling recommendations
print("To achieve 10K+ IOPS (3x current throughput), consider:")
print()

# Get current max utilizations
max_spark_cpu = max(spark_data['cpu_percent']) if spark_data['cpu_percent'] else 0
max_yb_cpu = max(yugabyte_data['cpu_percent']) if yugabyte_data['cpu_percent'] else 0
max_cass_cpu = max(cassandra_data['cpu_percent']) if cassandra_data['cpu_percent'] else 0
max_sys_cpu = max(system_data['cpu_percent']) if system_data['cpu_percent'] else 0

print("1. CPU Requirements:")
if max_sys_cpu > 80:
    print(f"   Current System CPU: {max_sys_cpu:.1f}%")
    print(f"   Recommended: 3-4x current CPU capacity")
    print(f"   → Need: More CPU cores or dedicated server")
if max_yb_cpu > 80:
    print(f"   Current Yugabyte CPU: {max_yb_cpu:.1f}%")
    print(f"   Recommended: 3-4x Yugabyte CPU capacity")
    print(f"   → Need: More Yugabyte nodes or faster CPU")
if max_cass_cpu > 80:
    print(f"   Current Cassandra CPU: {max_cass_cpu:.1f}%")
    print(f"   Recommended: 3-4x Cassandra CPU capacity")
    print(f"   → Need: More Cassandra nodes or faster CPU")

print()
print("2. Memory Requirements:")
max_spark_mem = max(spark_data['mem_mb']) if spark_data['mem_mb'] else 0
max_yb_mem = max(yugabyte_data['mem_mb']) if yugabyte_data['mem_mb'] else 0
max_cass_mem = max(cassandra_data['mem_mb']) if cassandra_data['mem_mb'] else 0

if max_spark_mem > 0:
    print(f"   Current Spark Memory: {max_spark_mem:.0f}MB")
    print(f"   Recommended: 2-3x current ({(max_spark_mem*2.5)/1024:.1f}GB)")
if max_yb_mem > 0:
    print(f"   Current Yugabyte Memory: {max_yb_mem:.0f}MB")
    print(f"   Recommended: 2-3x current ({(max_yb_mem*2.5)/1024:.1f}GB)")
if max_cass_mem > 0:
    print(f"   Current Cassandra Memory: {max_cass_mem:.0f}MB")
    print(f"   Recommended: 2-3x current ({(max_cass_mem*2.5)/1024:.1f}GB)")

print()
print("3. Infrastructure Recommendations:")
print("   For 10K+ IOPS, consider:")
print("   - Dedicated server with 16+ CPU cores")
print("   - 32GB+ RAM")
print("   - SSD storage for databases")
print("   - Separate nodes for Spark, Yugabyte, and Cassandra")
print("   - Network: 10Gbps or better")
print()

print("=" * 80)
print(f"Detailed CSV files available in: {monitor_dir}/")
print("=" * 80)
PYTHON_EOF

    echo ""
    echo "Analysis complete!"
else
    echo "⚠️  No monitoring data found"
fi

exit $MIGRATION_EXIT

