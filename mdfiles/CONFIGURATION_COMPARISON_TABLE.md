# Configuration Comparison: Current vs 3K+ IOPS Configurations

## Comprehensive Comparison Table

| Parameter | Current (28 Parts) | 40 Parts (3,159 IOPS) | 24 Parts (3,192 Peak) | Difference (28 vs 40) | Difference (28 vs 24 Peak) |
|-----------|-------------------|----------------------|----------------------|----------------------|---------------------------|
| **PERFORMANCE METRICS** |
| Average IOPS | 2,874 | **3,159** | 2,554 | -285 (-9.9%) | +320 (+12.5%) |
| Peak IOPS | - | 3,930 | **3,192** | - | - |
| Duration | 87.0s | **79.15s** | 97.0s | +7.85s (+9.9%) | -10.0s (-10.3%) |
| Record Count | 250,000 | 250,000 | 250,000 | Same | Same |
| Success Rate | 100% | 100% | 100% | Same | Same |
| **PARTITION CONFIGURATION** |
| Number of Partitions | 28 | **40** | 24 | -12 (-30%) | +4 (+16.7%) |
| Spark Parallelism | 80 | 20 | 24 | +60 | +56 |
| **BATCH CONFIGURATION** |
| Yugabyte Batch Size | 100 | **50** | 100 | +50 (+100%) | Same |
| CDM Batch Size | 50 | 25 | 50 | +25 (+100%) | Same |
| Fetch Size (Rows) | 5,000 | **2,000** | 5,000 | +3,000 (+150%) | Same |
| **CONNECTION POOL** |
| Pool Max Size | 5 | **3** | 5 | +2 (+66.7%) | Same |
| Pool Min Size | 2 | 1 | 2 | +1 (+100%) | Same |
| Total Connections | 140 | **120** | 120 | +20 (+16.7%) | +20 (+16.7%) |
| **SPARK CONFIGURATION** |
| Master | local[*] | **local[20]** | local[*] | Different | Same |
| Driver Memory | 8G | **3G** | 8G | +5G (+166.7%) | Same |
| Executor Memory | 8G | 1G | 8G | +7G (+700%) | Same |
| Executor Cores | 4 | 1 | 4 | +3 (+300%) | Same |
| Default Parallelism | 80 | 20 | 80 | +60 (+300%) | Same |
| Shuffle Partitions | 80 | 20 | 80 | +60 (+300%) | Same |
| Memory Overhead | 2G | - | 2G | +2G | Same |
| Memory Fraction | 0.8 | - | 0.8 | +0.8 | Same |
| **RATE LIMITS** |
| Origin Rate Limit | 20,000 | 20,000 | 20,000 | Same | Same |
| Target Rate Limit | 20,000 | 20,000 | 20,000 | Same | Same |
| **OTHER SETTINGS** |
| rewriteBatchedInserts | true | true | true | Same | Same |
| Connection Timeout | 120000 | - | 120000 | +120s | Same |
| Idle Timeout | 300000 | - | 300000 | +300s | Same |
| Max Lifetime | 1800000 | - | 1800000 | +1800s | Same |

---

## Key Differences Analysis

### 1. Partition Count
- **Current (28):** Middle ground between 24 and 40
- **40 Parts:** More parallelism, better average throughput
- **24 Parts:** Fewer partitions, higher peak but lower average

### 2. Batch Configuration
- **Current (28):** Uses larger batches (100) and fetch size (5000)
- **40 Parts:** Uses smaller batches (50) and fetch size (2000) - **More efficient**
- **24 Parts:** Same as current (100/5000)

### 3. Connection Pool
- **Current (28):** 140 total connections (28 × 5)
- **40 Parts:** 120 total connections (40 × 3) - **More efficient**
- **24 Parts:** 120 total connections (24 × 5)

### 4. Spark Configuration
- **Current (28):** More aggressive (8G memory, 4 cores, 80 parallelism)
- **40 Parts:** More conservative (3G driver, 1 core, 20 parallelism) - **More efficient**
- **24 Parts:** Same as current

---

## Performance Gap Analysis

### Why Current (28) is Below 3K IOPS:

1. **Batch Size Too Large:**
   - Current: 100 records/batch
   - 40 Parts: 50 records/batch
   - **Impact:** Larger batches may cause longer wait times

2. **Fetch Size Too Large:**
   - Current: 5,000 rows
   - 40 Parts: 2,000 rows
   - **Impact:** Larger fetch size may cause memory pressure

3. **Connection Pool Too Large:**
   - Current: 140 connections (28 × 5)
   - 40 Parts: 120 connections (40 × 3)
   - **Impact:** More connections may cause contention

4. **Spark Overhead:**
   - Current: 80 parallelism (very high)
   - 40 Parts: 20 parallelism (matches partitions)
   - **Impact:** Too much parallelism can cause overhead

---

## Recommendations to Reach 3K+ IOPS

### Option 1: Match 40-Partition Configuration (Best Average)
```properties
spark.cdm.perfops.numParts=40
spark.cdm.connect.target.yugabyte.batchSize=50
spark.cdm.perfops.fetchSizeInRows=2000
spark.cdm.connect.target.yugabyte.pool.maxSize=3
spark.cdm.connect.target.yugabyte.pool.minSize=1
```

Spark Config:
```bash
--master local[20]
--driver-memory 3G
--executor-memory 1G
--executor-cores 1
--conf spark.default.parallelism=20
--conf spark.sql.shuffle.partitions=20
```

**Expected:** 3,159 IOPS average, 3,930 IOPS peak

### Option 2: Optimize Current 28-Partition Configuration
```properties
spark.cdm.perfops.numParts=28
spark.cdm.connect.target.yugabyte.batchSize=50  # Reduce from 100
spark.cdm.perfops.fetchSizeInRows=2000        # Reduce from 5000
spark.cdm.connect.target.yugabyte.pool.maxSize=3  # Reduce from 5
```

Spark Config:
```bash
--master local[*]
--driver-memory 8G
--conf spark.default.parallelism=28  # Match partitions
--conf spark.sql.shuffle.partitions=28
```

**Expected:** 3,000-3,200 IOPS

### Option 3: Hybrid Approach (28 Partitions, Optimized Settings)
```properties
spark.cdm.perfops.numParts=28
spark.cdm.connect.target.yugabyte.batchSize=75  # Between 50 and 100
spark.cdm.perfops.fetchSizeInRows=3000         # Between 2000 and 5000
spark.cdm.connect.target.yugabyte.pool.maxSize=4  # Between 3 and 5
```

**Expected:** 2,900-3,100 IOPS

---

## Summary

| Metric | Current (28) | 40 Parts (Best) | Gap |
|--------|--------------|------------------|-----|
| Average IOPS | 2,874 | 3,159 | -285 (-9.9%) |
| Peak IOPS | - | 3,930 | - |
| Duration | 87s | 79s | +8s slower |
| Efficiency | Good | **Best** | - |

**Conclusion:** The 40-partition configuration with smaller batch/fetch sizes and optimized connection pool is the most efficient setup for achieving 3K+ IOPS consistently.
