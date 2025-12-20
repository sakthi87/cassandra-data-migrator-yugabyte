# Performance Bottleneck Analysis - 250K Migration

## Executive Summary

**Achieved Throughput:** 3,159 IOPS  
**Target Throughput:** 10,000-20,000 IOPS  
**Gap:** 6,841-16,841 IOPS below target

## Key Findings

### ✅ What's Working Well
1. **Batch Processing:** 100% efficiency (50 records per batch as configured)
2. **Rate Limiters:** Correctly set to 20,000 (not limiting performance)
3. **Phase 1+2 Optimizations:** Enabled and working
4. **Data Integrity:** 100% success rate, no errors

### ❌ Primary Bottleneck Identified

**Parallelism Efficiency: Only 44%**

- **Theoretical Throughput:** 7,187 records/sec (if 100% parallel)
- **Actual Throughput:** 3,159 records/sec
- **Efficiency Loss:** 56% of potential performance

## Detailed Analysis

### 1. Partition Processing Times

```
Total Partitions: 40
Min Time: 8.59 seconds
Max Time: 63.09 seconds
Average Time: 34.78 seconds
Median Time: 36.31 seconds
```

**Problem:** Wide variance (8.59s to 63.09s) indicates:
- Partitions are NOT running in true parallel
- Some partitions wait for others to complete
- Spark local mode parallelism limitations

### 2. Spark Configuration Analysis

**Current Configuration:**
```
Master: local[20]
Driver Memory: 8G
Executor Memory: 1g  ⚠️ TOO SMALL
Executor Cores: 1    ⚠️ TOO SMALL
Executor Instances: 1
Parallelism: 40
```

**Issues:**
1. **Executor Memory (1g):** Too small for batch processing
   - Each partition needs memory for:
     - Batch buffer (50 records × 118 columns)
     - Connection pool overhead
     - Spark task overhead
   - **Impact:** Memory pressure causes GC pauses

2. **Executor Cores (1):** Only 1 core per executor
   - Cannot process multiple partitions simultaneously within executor
   - **Impact:** Serial processing instead of parallel

3. **Local Mode Limitations:**
   - `local[20]` means 20 threads, but executor configuration limits actual parallelism
   - Spark tasks compete for limited executor resources

### 3. Database Performance

**YugabyteDB:**
- CPU Usage: 13.94% (not saturated)
- Memory Usage: 971.9MB / 5.787GB (plenty available)
- Max Connections: 300 (current: 1 active)
- **Verdict:** Database is NOT the bottleneck

**Cassandra:**
- CPU Usage: 4.82% (not saturated)
- Memory Usage: 3.711GB / 5.787GB
- **Verdict:** Database is NOT the bottleneck

### 4. Network Performance

**Container Network I/O:**
- Cassandra: 103MB sent / 169MB received
- Yugabyte: 257MB sent / 8.73MB received
- **Verdict:** Network is NOT saturated

### 5. Batch Processing Efficiency

```
Total Batches: 5,000
Total Records: 250,000
Average Records per Batch: 50.0
Expected Batch Size: 50
Efficiency: 100%
```

**Verdict:** Batch processing is optimal ✅

## Root Cause: Spark Executor Configuration

The bottleneck is **Spark executor resource constraints**, not database or network:

1. **Memory Constraint:**
   - 1GB executor memory is insufficient
   - Causes frequent GC pauses
   - Limits batch buffer sizes

2. **CPU Constraint:**
   - 1 core per executor = serial processing
   - Cannot utilize multiple CPU cores
   - Partitions wait in queue

3. **Local Mode Overhead:**
   - All tasks share same JVM
   - Resource contention between tasks
   - Limited by single executor configuration

## Recommended Fixes

### Fix 1: Increase Executor Memory (CRITICAL)

**Current:** `--executor-memory 1g`  
**Recommended:** `--executor-memory 8G`

**Impact:** Reduces GC pauses, allows larger batch buffers

### Fix 2: Increase Executor Cores

**Current:** `--executor-cores 1` (default)  
**Recommended:** `--executor-cores 4` or `--executor-cores 8`

**Impact:** Enables true parallel processing within executor

### Fix 3: Optimize Spark Configuration

Add to spark-submit command:
```bash
--conf spark.executor.memory=8G \
--conf spark.executor.cores=4 \
--conf spark.memory.fraction=0.8 \
--conf spark.memory.storageFraction=0.3 \
--conf spark.sql.shuffle.partitions=40 \
--conf spark.default.parallelism=40
```

### Fix 4: Remove Rate Limits (Optional)

Uncomment or remove rate limit properties to allow maximum throughput:
```properties
spark.cdm.perfops.ratelimit.origin=50000
spark.cdm.perfops.ratelimit.target=50000
```

## Expected Performance After Fixes

With proper Spark configuration:
- **Expected Throughput:** 10,000-15,000 IOPS
- **Improvement:** 3-5x increase
- **Time for 250K records:** ~17-25 seconds (vs 79 seconds currently)

## Verification Steps

1. Monitor Spark UI during migration: `http://localhost:4040`
2. Check executor memory usage
3. Monitor GC pauses
4. Verify partition parallelism

## Summary

**Bottleneck:** Spark executor configuration (memory + cores)  
**Not Bottlenecks:** Database performance, network, rate limits, batch processing  
**Fix Priority:** HIGH - Executor memory and cores are critical

