# Resource Utilization Analysis Guide

## Overview

This guide helps you identify bottlenecks during data migration by monitoring resource utilization across Spark, Yugabyte, Cassandra, and your Mac system.

## Step-by-Step Process

### Step 1: Run Migration with Monitoring

```bash
./run_migration_with_monitoring.sh
```

This script will:
1. Start resource monitoring in the background
2. Run the migration
3. Stop monitoring when migration completes
4. Generate a comprehensive analysis report

### Step 2: Understanding the Metrics

#### What Each Metric Means:

**CPU Utilization:**
- **0-50%**: Low utilization, not a bottleneck
- **50-70%**: Moderate utilization, may be limiting
- **70-90%**: High utilization, likely bottleneck
- **90-100%**: Critical bottleneck, resource is saturated

**Memory Utilization:**
- **0-70%**: Adequate memory
- **70-90%**: High memory usage, may cause swapping
- **90-100%**: Critical, memory is exhausted

**Load Average:**
- Should be < number of CPU cores
- If load > cores: System is overloaded

### Step 3: Identifying Bottlenecks

#### Read Bottleneck (Cassandra)
**Indicators:**
- Cassandra CPU > 80%
- Cassandra Memory > 90%
- Spark waiting for data from Cassandra

**Solutions:**
- Increase Cassandra container resources
- Add more Cassandra nodes
- Optimize read queries
- Increase fetch size

#### Write Bottleneck (Yugabyte)
**Indicators:**
- Yugabyte CPU > 80%
- Yugabyte Memory > 90%
- Connection pool exhausted
- Write queue building up

**Solutions:**
- Increase Yugabyte container resources
- Add more Yugabyte nodes
- Optimize batch size
- Increase connection pool size
- Use faster storage (SSD)

#### Spark Processing Bottleneck
**Indicators:**
- Spark CPU > 80%
- Spark Memory > 7GB
- High thread count
- Spark tasks queuing

**Solutions:**
- Increase Spark executor cores
- Increase Spark memory
- Optimize partition count
- Reduce parallelism if causing contention

#### System Resource Bottleneck (Mac)
**Indicators:**
- System CPU > 90%
- System Memory > 90%
- Load average > CPU cores
- System swapping

**Solutions:**
- Close other applications
- Increase system resources (if possible)
- Use dedicated server
- Reduce overall load

### Step 4: Interpreting Results

#### Example Analysis Output:

```
BOTTLENECK IDENTIFICATION
────────────────────────────────────────────────────────
🔴 1. Yugabyte CPU: 95.2% (CRITICAL)
   → WRITE BOTTLENECK - Yugabyte write operations
🟡 2. System CPU: 87.3% (HIGH)
   → SYSTEM RESOURCE BOTTLENECK - Mac CPU high
```

**Interpretation:**
- Primary bottleneck: Yugabyte writes (CPU at 95%)
- Secondary bottleneck: System CPU (87%)
- **Conclusion:** Write operations are the limiting factor, but system resources are also constrained

### Step 5: Resource Requirements for Higher Throughput

#### Current System (Mac):
- **Achieved:** ~3,049 IOPS
- **Target:** 10,000+ IOPS (3x increase)

#### Required Resources for 10K IOPS:

**CPU:**
- Current: 6-8 cores (Mac)
- Required: 16-24 cores
- **Recommendation:** Dedicated server with 16+ cores

**Memory:**
- Current: ~16GB (Mac)
- Required: 32-64GB
- **Recommendation:** 32GB minimum, 64GB preferred

**Storage:**
- Current: Docker volumes (may be on HDD)
- Required: SSD with high IOPS
- **Recommendation:** NVMe SSD with 50K+ IOPS

**Network:**
- Current: Localhost (very fast)
- Required: Low latency network
- **Recommendation:** 10Gbps network for distributed setup

**Infrastructure:**
- **Current:** Single Mac running everything
- **Recommended:** Distributed setup:
  - Separate Spark cluster (4-8 nodes)
  - Yugabyte cluster (3+ nodes)
  - Cassandra cluster (3+ nodes)

### Step 6: Articulating Requirements

#### For Higher System Resources:

**Current Limitations:**
1. **CPU:** Mac CPU at 87% - need 3x capacity
2. **Memory:** System memory constrained - need 2x capacity
3. **Storage:** Docker volumes may be on slower storage
4. **Architecture:** Single machine running all components

**Recommended Infrastructure:**

```
┌─────────────────────────────────────────────────────┐
│ Production Environment for 10K+ IOPS                │
├─────────────────────────────────────────────────────┤
│                                                       │
│  Spark Cluster:                                      │
│  - 4-8 nodes, 8 cores each, 16GB RAM                │
│  - Total: 32-64 cores, 64-128GB RAM                 │
│                                                       │
│  Yugabyte Cluster:                                   │
│  - 3 nodes, 16 cores each, 32GB RAM, SSD             │
│  - Total: 48 cores, 96GB RAM                         │
│                                                       │
│  Cassandra Cluster:                                  │
│  - 3 nodes, 8 cores each, 16GB RAM, SSD             │
│  - Total: 24 cores, 48GB RAM                         │
│                                                       │
│  Network:                                            │
│  - 10Gbps between nodes                              │
│  - Low latency (<1ms)                               │
│                                                       │
└─────────────────────────────────────────────────────┘
```

**Expected Performance:**
- **10K-15K IOPS** with proper configuration
- **20K+ IOPS** with aggressive tuning

### Step 7: Validation Checklist

After running monitoring, check:

- [ ] Which component has highest CPU usage?
- [ ] Which component has highest memory usage?
- [ ] Is system CPU saturated?
- [ ] Is system memory saturated?
- [ ] Are there any error patterns?
- [ ] What is the peak resource utilization?
- [ ] Are resources consistently high or spiky?

### Step 8: Next Steps Based on Results

**If Yugabyte is the bottleneck:**
1. Increase Yugabyte container resources
2. Optimize batch size and connection pool
3. Consider Yugabyte cluster (multiple nodes)
4. Use faster storage (SSD)

**If Cassandra is the bottleneck:**
1. Increase Cassandra container resources
2. Optimize fetch size
3. Consider Cassandra cluster
4. Tune read consistency levels

**If Spark is the bottleneck:**
1. Increase Spark executor resources
2. Optimize partition count
3. Tune Spark configuration
4. Consider Spark cluster

**If System is the bottleneck:**
1. Close other applications
2. Use dedicated server
3. Increase system resources
4. Consider distributed architecture

## Example Analysis Report

After running the monitoring, you'll get a report like:

```
COMPREHENSIVE RESOURCE UTILIZATION ANALYSIS
================================================================================

1. SPARK PROCESS RESOURCES
────────────────────────────────────────────────────────────────────────────────
   CPU Usage:
     Average: 45.2%
     Maximum: 78.5%
     Median:  42.1%
   Memory Usage:
     Average: 3245.3 MB
     Maximum: 4567.8 MB
     Median:  3123.4 MB

2. YUGABYTE CONTAINER RESOURCES (WRITE OPERATIONS)
────────────────────────────────────────────────────────────────────────────────
   CPU Usage:
     Average: 72.3%
     Maximum: 95.2%
     Median:  68.9%
   Memory Usage (%):
     Average: 68.5%
     Maximum: 82.3%

3. CASSANDRA CONTAINER RESOURCES (READ OPERATIONS)
────────────────────────────────────────────────────────────────────────────────
   CPU Usage:
     Average: 35.4%
     Maximum: 58.2%
     Median:  32.1%

4. SYSTEM RESOURCES (MAC)
────────────────────────────────────────────────────────────────────────────────
   CPU Usage:
     Average: 78.5%
     Maximum: 95.3%
     Median:  75.2%
   Load Average:
     Average: 6.23
     Maximum: 8.45

BOTTLENECK IDENTIFICATION
────────────────────────────────────────────────────────────────────────────────

🔴 1. Yugabyte CPU: 95.2% (CRITICAL)
   → WRITE BOTTLENECK - Yugabyte write operations
🔴 2. System CPU: 95.3% (CRITICAL)
   → SYSTEM RESOURCE BOTTLENECK - Mac CPU saturated

RECOMMENDATIONS:
   1. Yugabyte is CPU-bound. Consider: more Yugabyte nodes, faster CPU, or reduce write load
   2. System CPU is the bottleneck. Need: More CPU cores or faster CPU
```

## Files Generated

After monitoring, you'll find:

- `resource_monitoring_YYYYMMDD_HHMMSS/` - Directory with CSV files
  - `spark_stats.csv` - Spark process metrics
  - `yugabyte_stats.csv` - Yugabyte container metrics
  - `cassandra_stats.csv` - Cassandra container metrics
  - `system_stats.csv` - System-wide metrics
  - `network_stats.csv` - Network I/O metrics

## Manual Monitoring (Alternative)

If you prefer to monitor manually:

```bash
# Terminal 1: Monitor Spark
watch -n 1 'ps aux | grep spark-submit | grep -v grep'

# Terminal 2: Monitor Yugabyte
watch -n 1 'docker stats yugabyte --no-stream'

# Terminal 3: Monitor Cassandra
watch -n 1 'docker stats cassandra --no-stream'

# Terminal 4: Monitor System
top -l 1 | head -20
```

## Questions to Answer

After analysis, you should be able to answer:

1. **What is the primary bottleneck?**
   - Read (Cassandra)
   - Write (Yugabyte)
   - Processing (Spark)
   - System resources (Mac)

2. **Is this the maximum for my system?**
   - Compare max utilization vs available resources
   - Check if any resource is at 100%

3. **What resources are needed for higher throughput?**
   - Based on current utilization
   - Calculate 3x requirements for 10K IOPS

4. **How to articulate requirements?**
   - Document current limitations
   - Specify required resources
   - Provide infrastructure recommendations

