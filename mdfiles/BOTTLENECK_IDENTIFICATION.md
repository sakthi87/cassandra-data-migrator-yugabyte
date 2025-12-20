# Bottleneck Identification Guide

## Quick Summary

**Achieved:** 3,159 IOPS  
**Target:** 10,000-20,000 IOPS  
**Primary Bottleneck:** Spark Executor Configuration (Memory + CPU Cores)

---

## Bottleneck Identification Results

### ✅ NOT Bottlenecks

1. **Rate Limits:** 20,000 (not 1,000) - Well above achieved throughput
2. **Database Performance:** 
   - Cassandra CPU: 4.82% (plenty of headroom)
   - YugabyteDB CPU: 13.94% (plenty of headroom)
   - Connections: 1/300 active (plenty available)
3. **Network:** Not saturated (103MB/169MB for Cassandra, 257MB/8.73MB for Yugabyte)
4. **Batch Processing:** 100% efficient (50 records per batch as configured)

### ❌ ACTUAL Bottlenecks

#### 1. Spark Executor Memory (CRITICAL)
- **Current:** 1g
- **Problem:** Too small for batch processing
- **Impact:** Frequent GC pauses, memory pressure
- **Evidence:** 
  - Wide partition time variance (8.59s to 63.09s)
  - Only 44% parallelism efficiency
- **Fix:** Increase to 8G

#### 2. Spark Executor Cores (CRITICAL)
- **Current:** 1 core
- **Problem:** Cannot process multiple partitions in parallel
- **Impact:** Serial processing instead of parallel
- **Evidence:**
  - Average partition time: 34.78 seconds
  - With 40 partitions, should complete in ~35s if truly parallel
  - But total time is 79s, indicating serialization
- **Fix:** Increase to 4-8 cores

#### 3. Parallelism Efficiency (RESULT)
- **Current:** 44%
- **Theoretical:** 7,187 records/sec (if 100% parallel)
- **Actual:** 3,159 records/sec
- **Loss:** 4,028 records/sec (56%)
- **Root Cause:** Executor memory + cores constraints

---

## How to Verify Bottlenecks

### 1. Check Spark Configuration
```bash
ps aux | grep spark-submit | grep -E "executor-memory|executor-cores"
```
**Current (Bottleneck):**
- `executor-memory 1g` ❌
- `executor-cores 1` ❌ (or not set, defaults to 1)

**Should be:**
- `executor-memory 8G` ✅
- `executor-cores 4` ✅

### 2. Monitor Resource Usage
```bash
# Container resources
docker stats cassandra yugabyte

# Spark process memory
ps aux | grep spark-submit | awk '{print $6/1024 " MB"}'
```

**Bottleneck Indicators:**
- Executor memory < 4GB → Likely bottleneck
- Database CPU < 50% → Database NOT bottleneck
- Network I/O low → Network NOT bottleneck

### 3. Check Partition Processing Times
```bash
grep "Finished task.*in.*ms" migration_250k_*.log | awk '{print $NF}' | sort -n
```

**Bottleneck Indicators:**
- Wide variance (min << max) → Resource contention
- Average time >> (total_time / num_partitions) → Serialization

### 4. Check Parallelism Efficiency
```python
# Calculate from logs
avg_partition_time = sum(partition_times) / len(partition_times)
records_per_partition = total_records / num_partitions
throughput_per_partition = records_per_partition / avg_partition_time
theoretical_throughput = throughput_per_partition * num_partitions
actual_throughput = total_records / total_time
efficiency = (actual_throughput / theoretical_throughput) * 100

# If efficiency < 60% → Parallelism bottleneck
```

---

## Fix Verification

After applying fixes, verify:

1. **Spark Configuration:**
   ```bash
   ps aux | grep spark-submit | grep "executor-memory 8G"
   ps aux | grep spark-submit | grep "executor-cores 4"
   ```

2. **Performance Improvement:**
   - Expected: 10,000-15,000 IOPS
   - Time for 250K: ~17-25 seconds
   - Parallelism efficiency: > 80%

3. **Resource Usage:**
   - Executor memory: Should use 4-6GB (not maxed out)
   - Database CPU: Should increase to 30-50% (better utilization)
   - GC pauses: Should decrease significantly

---

## Summary

**Bottleneck:** Spark executor configuration (memory + cores)  
**Not Bottlenecks:** Database, network, rate limits, batch processing  
**Fix:** Increase executor memory to 8G and cores to 4  
**Expected Improvement:** 3-5x performance increase (10K-15K IOPS)

