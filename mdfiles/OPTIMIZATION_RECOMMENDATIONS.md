# Optimization Recommendations for Local Machine

## Executive Summary

**Current Performance:** 614 IOPS (unacceptable)  
**Target:** Maximum achievable on local machine  
**System Capacity:** 8GB RAM, 8 CPU cores, Docker containers

**Root Cause:** Configuration exceeds available resources
- 80 partitions on 6 available cores = excessive overhead
- 8G driver memory exceeds 3.3GB available
- Over-partitioning causes context switching overhead

---

## System Capacity Analysis

### Your Machine:
- **Total Memory:** 8 GB
- **Total CPU Cores:** 8 cores
- **Docker Usage:**
  - Cassandra: 3.7 GB
  - YugabyteDB: 1.0 GB
  - **Total:** 4.7 GB

### Available for Spark:
- **Memory:** ~3.3 GB (8 GB - 4.7 GB)
- **CPU Cores:** 6 cores (8 - 2 for OS/Docker)

---

## Recommended Configurations

### 🟢 Option 1: CONSERVATIVE (Start Here)
**Best for:** Stability, baseline testing

```bash
./optimize_for_local_machine.sh conservative
```

**Settings:**
- Spark: `local[6]`
- Driver Memory: 2G
- Parallelism: 12
- Partitions: 12
- Batch Size: 50
- Fetch Size: 2,000

**Expected:** 2,000-3,000 IOPS  
**Risk:** Low  
**Memory Usage:** ~2.5 GB

---

### 🟡 Option 2: BALANCED (Recommended)
**Best for:** Best performance/stability trade-off

```bash
./optimize_for_local_machine.sh balanced
```

**Settings:**
- Spark: `local[6]`
- Driver Memory: 3G
- Parallelism: 18
- Partitions: 18
- Batch Size: 75
- Fetch Size: 3,000

**Expected:** 3,000-4,500 IOPS  
**Risk:** Medium  
**Memory Usage:** ~3.0 GB

---

### 🔴 Option 3: AGGRESSIVE (Maximum)
**Best for:** Finding maximum capacity

```bash
./optimize_for_local_machine.sh aggressive
```

**Settings:**
- Spark: `local[6]`
- Driver Memory: 3G
- Parallelism: 24
- Partitions: 24
- Batch Size: 100
- Fetch Size: 5,000

**Expected:** 4,000-6,000 IOPS  
**Risk:** High (may swap)  
**Memory Usage:** ~3.2 GB

---

## Configuration Comparison

| Setting | Current (Bad) | Conservative | Balanced | Aggressive |
|---------|--------------|--------------|----------|------------|
| **Spark Cores** | 8 (all) | 6 | 6 | 6 |
| **Driver Memory** | 8G ❌ | 2G ✅ | 3G ✅ | 3G ✅ |
| **Parallelism** | 80 ❌ | 12 ✅ | 18 ✅ | 24 ✅ |
| **Partitions** | 80 ❌ | 12 ✅ | 18 ✅ | 24 ✅ |
| **Batch Size** | 100 | 50 | 75 | 100 |
| **Fetch Size** | 5,000 | 2,000 | 3,000 | 5,000 |
| **Expected IOPS** | 614 | 2K-3K | 3K-4.5K | 4K-6K |
| **Improvement** | Baseline | 3-5x | 5-7x | 6-10x |

---

## Why Current Config Fails

### Problem 1: Over-Partitioning
- **80 partitions** on 6 cores = 13 partitions per core
- **Result:** Excessive context switching, overhead
- **Fix:** Reduce to 12-24 partitions (2-4x cores)

### Problem 2: Memory Exhaustion
- **8G driver memory** exceeds 3.3GB available
- **Result:** Swapping, GC pauses, performance degradation
- **Fix:** Reduce to 2-3G (within available memory)

### Problem 3: Resource Contention
- **local[*]** uses all 8 cores
- **Result:** Competing with Docker containers
- **Fix:** Use `local[6]` (leave 2 cores for OS/Docker)

---

## Recommended Approach

### Step 1: Start with Balanced
```bash
# Clean up Yugabyte
docker exec yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore -c "TRUNCATE TABLE dda_pstd_fincl_txn_cnsmr_by_accntnbr;"'

# Run balanced configuration
./optimize_for_local_machine.sh balanced
```

### Step 2: Monitor Performance
- Watch CPU usage (should be 60-80%)
- Monitor memory (should stay under 3GB)
- Check for errors/swapping

### Step 3: Adjust Based on Results
- **If stable and CPU < 80%:** Try aggressive
- **If unstable or swapping:** Try conservative
- **If memory pressure:** Reduce batch size

### Step 4: Fine-tune
- **CPU saturated?** Reduce partitions
- **Memory pressure?** Reduce batch/fetch size
- **Network bottleneck?** Increase batch (if memory allows)

---

## Expected Performance Benchmarks

### Conservative:
- **IOPS:** 2,000-3,000
- **Time (250K):** ~83-125 seconds
- **Stability:** High

### Balanced (Recommended):
- **IOPS:** 3,000-4,500
- **Time (250K):** ~56-83 seconds
- **Stability:** Good

### Aggressive:
- **IOPS:** 4,000-6,000
- **Time (250K):** ~42-63 seconds
- **Stability:** Medium

---

## Key Principles

### 1. Memory Management
- ✅ Never exceed 3.3GB available
- ✅ Leave 10-20% margin for spikes
- ✅ Monitor for swapping

### 2. CPU Utilization
- ✅ Use 6 cores max (leave 2 for OS/Docker)
- ✅ Match parallelism to cores (2-4x)
- ✅ Avoid over-partitioning

### 3. Partition Count
- ✅ **Rule:** 2-4x available cores
- ✅ **For 6 cores:** 12-24 partitions optimal
- ✅ **Too many:** Context switching overhead
- ✅ **Too few:** Underutilized CPU

---

## Monitoring Commands

```bash
# Resource usage
docker stats cassandra yugabyte

# Spark process
ps aux | grep spark-submit

# System memory (macOS)
vm_stat | head -10

# Migration progress
tail -f migration_optimized_*.log
```

---

## Next Steps

1. ✅ **Run balanced configuration:** `./optimize_for_local_machine.sh balanced`
2. ✅ **Monitor first 30 seconds:** Check CPU, memory, errors
3. ✅ **Record results:** Document achieved IOPS
4. ✅ **Adjust if needed:** Try conservative or aggressive
5. ✅ **Document benchmark:** This becomes your local machine baseline

---

## Production Environment

**For production (dedicated cluster):**
- More memory available → Can use 8G+ driver
- More cores available → Can use 40+ partitions
- No Docker overhead → Better performance
- **Expected:** 10K-20K IOPS achievable

**Local machine purpose:** Development, testing, benchmarking capacity limits.

