# Local Machine Optimization Guide

## System Capacity Analysis

### Your Machine Specifications:
- **Total Memory:** 8 GB
- **Total CPU Cores:** 8 cores
- **Docker Containers:**
  - Cassandra: ~3.7 GB
  - YugabyteDB: ~1.0 GB
  - **Total Docker:** ~4.7 GB

### Available Resources for Spark:
- **Available Memory:** ~3.3 GB (8 GB - 4.7 GB Docker)
- **Available CPU Cores:** 6 cores (8 - 2 for OS/Docker)

---

## Problem Analysis

### Current Configuration Issues:
1. **Too Many Partitions (80):** Overwhelming for 6 available cores
2. **Too Much Memory (8G driver):** Exceeds available 3.3 GB
3. **local[*]:** Uses all 8 cores, competing with Docker containers
4. **Large Batch/Fetch:** May cause memory pressure

### Why 614 IOPS is Low:
- **Over-partitioning:** 80 partitions on 6 cores = excessive context switching
- **Memory pressure:** 8G driver memory causes swapping/GC pauses
- **Resource contention:** Spark competing with Docker containers

---

## Recommended Configurations

### Option 1: CONSERVATIVE (Stability First)
**Best for:** Initial testing, ensuring stability

```bash
./optimize_for_local_machine.sh conservative
```

**Configuration:**
- Spark Cores: 6
- Driver Memory: 2G
- Parallelism: 12
- Partitions: 12
- Batch Size: 50
- Fetch Size: 2,000

**Expected Performance:** 2,000-3,000 IOPS  
**Memory Usage:** ~2.5 GB (safe margin)  
**Risk Level:** Low

---

### Option 2: BALANCED (Recommended)
**Best for:** Best performance/stability trade-off

```bash
./optimize_for_local_machine.sh balanced
```

**Configuration:**
- Spark Cores: 6
- Driver Memory: 3G
- Parallelism: 18
- Partitions: 18
- Batch Size: 75
- Fetch Size: 3,000

**Expected Performance:** 3,000-4,500 IOPS  
**Memory Usage:** ~3.0 GB (optimal)  
**Risk Level:** Medium

---

### Option 3: AGGRESSIVE (Maximum Throughput)
**Best for:** Pushing limits, finding maximum capacity

```bash
./optimize_for_local_machine.sh aggressive
```

**Configuration:**
- Spark Cores: 6
- Driver Memory: 3G
- Parallelism: 24
- Partitions: 24
- Batch Size: 100
- Fetch Size: 5,000

**Expected Performance:** 4,000-6,000 IOPS  
**Memory Usage:** ~3.2 GB (tight)  
**Risk Level:** High (may cause swapping)

---

## Configuration Comparison

| Setting | Current | Conservative | Balanced | Aggressive |
|---------|---------|--------------|----------|------------|
| **Spark Cores** | 8 (all) | 6 | 6 | 6 |
| **Driver Memory** | 8G | 2G | 3G | 3G |
| **Parallelism** | 80 | 12 | 18 | 24 |
| **Partitions** | 80 | 12 | 18 | 24 |
| **Batch Size** | 100 | 50 | 75 | 100 |
| **Fetch Size** | 5,000 | 2,000 | 3,000 | 5,000 |
| **Expected IOPS** | 614 | 2K-3K | 3K-4.5K | 4K-6K |

---

## Optimization Strategy

### Step 1: Start with Balanced
```bash
docker exec yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore -c "TRUNCATE TABLE dda_pstd_fincl_txn_cnsmr_by_accntnbr;"'
./optimize_for_local_machine.sh balanced
```

### Step 2: Monitor and Adjust
- If stable and CPU < 80%: Try aggressive
- If unstable or swapping: Try conservative
- If memory pressure: Reduce batch/fetch size

### Step 3: Fine-tune Based on Results
- **If CPU saturated:** Reduce partitions
- **If memory pressure:** Reduce batch size or driver memory
- **If network bottleneck:** Increase batch size (if memory allows)

---

## Key Principles for Local Machine

### 1. Memory Management
- **Never exceed available memory:** 3.3 GB max
- **Leave 10-20% margin:** For OS and spikes
- **Monitor swapping:** If swapping occurs, reduce memory

### 2. CPU Utilization
- **Use 6 cores max:** Leave 2 for OS/Docker
- **Match parallelism to cores:** 2-4x cores is optimal
- **Avoid over-partitioning:** More partitions ≠ better performance

### 3. Batch/Fetch Size
- **Start smaller:** 50 batch, 2000 fetch
- **Increase if stable:** Monitor memory usage
- **Balance:** Larger = fewer round-trips but more memory

### 4. Partition Count
- **Rule of thumb:** 2-4x available cores
- **For 6 cores:** 12-24 partitions optimal
- **Too many:** Context switching overhead
- **Too few:** Underutilized CPU

---

## Expected Performance Benchmarks

### Conservative Configuration:
- **Throughput:** 2,000-3,000 IOPS
- **Duration (250K):** ~83-125 seconds
- **Stability:** High
- **Use Case:** Baseline, stability testing

### Balanced Configuration:
- **Throughput:** 3,000-4,500 IOPS
- **Duration (250K):** ~56-83 seconds
- **Stability:** Good
- **Use Case:** **Recommended starting point**

### Aggressive Configuration:
- **Throughput:** 4,000-6,000 IOPS
- **Duration (250K):** ~42-63 seconds
- **Stability:** Medium (may swap)
- **Use Case:** Maximum throughput testing

---

## Monitoring During Migration

### Check Resource Usage:
```bash
# CPU and Memory
docker stats cassandra yugabyte

# Spark Process
ps aux | grep spark-submit

# System Memory
vm_stat | head -10
```

### Watch for Warning Signs:
- **Swapping:** System becomes slow, disk I/O high
- **CPU 100%:** Reduce parallelism/partitions
- **Memory > 95%:** Reduce driver memory
- **GC pauses:** Increase memory or reduce batch size

---

## Next Steps

1. **Start with Balanced:** `./optimize_for_local_machine.sh balanced`
2. **Monitor first 30 seconds:** Check CPU, memory, errors
3. **Adjust if needed:**
   - Stable? → Try aggressive
   - Issues? → Try conservative
4. **Record results:** Document achieved IOPS for each config
5. **Fine-tune:** Adjust batch/fetch based on results

---

## Production Environment Considerations

For production (dedicated cluster):
- **More memory:** Can use 8G+ driver, larger batches
- **More cores:** Can use 40+ partitions
- **Dedicated resources:** No Docker overhead
- **Expected:** 10K-20K IOPS achievable

**Local machine is for:** Development, testing, benchmarking capacity limits.

