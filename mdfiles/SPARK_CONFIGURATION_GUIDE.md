# Spark Configuration Guide for 20K IOPS

## 🚨 Critical Issue Identified

Your Spark configuration is the **primary bottleneck**, not CDM settings!

**Current Spark Config (from your logs):**
```
Master: local[*]
Executor Memory: 1g          ❌ TOO SMALL
Executor Cores: 1             ❌ TOO SMALL  
Executor Instances: 1         ❌ TOO SMALL
Parallelism: 1                ❌ KILLER! Only 1 task at a time
```

**This means:**
- Only **1 task runs at a time** (parallelism=1)
- Only **1 CPU core** available (executor.cores=1)
- Only **1GB memory** per executor (too small for batching)
- **Rate limits: 1000/sec** (not 20000 as configured)

**Result:** ~1,826 records/sec instead of 20,000 records/sec

---

## 🔧 Required Spark Configuration

### Option 1: Standalone Cluster Mode (Recommended for On-Prem)

```bash
spark-submit \
  --master spark://your-spark-master:7077 \
  --deploy-mode cluster \
  --driver-memory 8G \
  --executor-memory 8G \
  --executor-cores 4 \
  --num-executors 5 \
  --conf spark.default.parallelism=80 \
  --conf spark.sql.shuffle.partitions=80 \
  --conf spark.executor.memoryOverhead=2G \
  --conf spark.network.timeout=600s \
  --conf spark.executor.heartbeatInterval=60s \
  --properties-file transaction-test-audit.properties \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

**Key Settings:**
- `num-executors=5` × `executor-cores=4` = **20 parallel tasks**
- `default.parallelism=80` = **80 partitions** (4x num-executors × cores)
- `executor-memory=8G` = Enough for batching
- `executor-cores=4` = Multiple cores per executor

---

### Option 2: Local Mode with More Resources (If no cluster available)

```bash
spark-submit \
  --master local[20] \
  --driver-memory 8G \
  --executor-memory 8G \
  --conf spark.default.parallelism=40 \
  --conf spark.sql.shuffle.partitions=40 \
  --conf spark.executor.memoryOverhead=2G \
  --conf spark.network.timeout=600s \
  --properties-file transaction-test-audit.properties \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

**Key Settings:**
- `local[20]` = Use **20 CPU cores** (adjust based on your 12-core machine)
- `default.parallelism=40` = **40 partitions** (2x cores)
- `executor-memory=8G` = Enough for batching

**Note:** On a 12-core machine, use `local[10]` to leave 2 cores for OS/other processes.

---

### Option 3: YARN Mode (If using Hadoop/YARN)

```bash
spark-submit \
  --master yarn \
  --deploy-mode cluster \
  --driver-memory 8G \
  --executor-memory 8G \
  --executor-cores 4 \
  --num-executors 5 \
  --conf spark.default.parallelism=80 \
  --conf spark.sql.shuffle.partitions=80 \
  --conf spark.executor.memoryOverhead=2G \
  --conf spark.network.timeout=600s \
  --properties-file transaction-test-audit.properties \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

---

## 📊 Configuration Breakdown

### Critical Spark Settings

| Setting | Current (Wrong) | Required | Why |
|---------|----------------|----------|-----|
| `spark.default.parallelism` | 1 | 40-80 | Controls number of parallel tasks |
| `spark.executor.cores` | 1 | 4-8 | CPU cores per executor |
| `spark.executor.memory` | 1g | 8g | Memory for batching |
| `spark.executor.instances` | 1 | 5-10 | Number of executors |
| `spark.sql.shuffle.partitions` | 1 | 40-80 | Shuffle partitions |

### Formula for Parallelism

```
spark.default.parallelism = num-executors × executor-cores × 2
```

**Example:**
- 5 executors × 4 cores × 2 = **40 parallelism**
- Or: 10 executors × 4 cores × 2 = **80 parallelism**

---

## 🔍 Why Your Current Config Fails

### Issue 1: Parallelism = 1
```
Parallelism: 1
```
**Impact:** Only 1 partition processes at a time, even though you have 20 partitions configured in CDM.

**Fix:** Set `spark.default.parallelism=40-80`

### Issue 2: Executor Cores = 1
```
Executor Cores: 1
```
**Impact:** Only 1 CPU core per executor = no parallel processing within executor.

**Fix:** Set `--executor-cores 4` (or 8 if available)

### Issue 3: Executor Memory = 1g
```
Executor Memory: 1g
```
**Impact:** Not enough memory for batching. Each batch needs memory for:
- Batch buffer (25-100 records)
- Connection pool
- Spark overhead

**Fix:** Set `--executor-memory 8G`

### Issue 4: Rate Limits Not Applied
```
Origin Rate Limit: 1000 reads/sec
Target Rate Limit: 1000 writes/sec
```
**But your properties file shows:**
```properties
spark.cdm.perfops.ratelimit.origin=20000
spark.cdm.perfops.ratelimit.target=20000
```

**Issue:** Spark might be using different property names or defaults.

**Fix:** Verify property names match exactly. Check if there are Spark-specific overrides.

---

## 🎯 Optimized Properties File

Update your `transaction-test-audit.properties`:

```properties
# =============================================================================
# PERFORMANCE SETTINGS - OPTIMIZED FOR 20K IOPS
# =============================================================================
# CRITICAL: Match numParts with Spark parallelism
# If Spark parallelism = 40, use numParts = 20-40
spark.cdm.perfops.numParts=40

# Batch size - increase for better throughput
spark.cdm.connect.target.yugabyte.batchSize=50

# Rate limits - remove or set very high
# Comment out to disable rate limiting
# spark.cdm.perfops.ratelimit.origin=50000
# spark.cdm.perfops.ratelimit.target=50000

# Fetch size - larger = fewer round trips
spark.cdm.perfops.fetchSizeInRows=2000

# Connection pool - adjust based on numParts
# Formula: maxSize = numParts / 8 (for 40 partitions = 5)
spark.cdm.connect.target.yugabyte.pool.maxSize=5
```

---

## 🌐 Network Latency Considerations

**Your Setup:**
- **Cassandra:** CBC & OBC data center (on-prem)
- **YugabyteDB:** Azure Central (cloud)
- **Spark:** On-prem (12 CPU, 64GB RAM)

**Network Impact:**
- Cross-datacenter latency: 10-50ms per round trip
- This affects both Cassandra reads and YugabyteDB writes

**Mitigation Strategies:**

1. **Increase Batch Size:**
   ```properties
   spark.cdm.connect.target.yugabyte.batchSize=100  # Larger batches = fewer round trips
   ```

2. **Increase Fetch Size:**
   ```properties
   spark.cdm.perfops.fetchSizeInRows=2000  # Larger fetches = fewer Cassandra queries
   ```

3. **Use Connection Pooling:**
   - Already configured, but ensure pool is large enough
   - Formula: `pool.maxSize = numParts / 8`

4. **Consider Data Locality:**
   - If possible, run Spark closer to YugabyteDB (Azure)
   - Or run Spark closer to Cassandra (on-prem) and accept YugabyteDB latency

---

## 📈 Expected Performance After Fixes

### Current Performance:
- **Throughput:** ~1,826 records/sec
- **Bottleneck:** Spark parallelism = 1

### After Fixes:

**With Proper Spark Config:**
- **Parallelism:** 40-80 tasks
- **Expected Throughput:** 15,000-25,000 records/sec
- **Target:** 20,000 records/sec ✅

**Calculation:**
- 40 parallel tasks × 500 records/sec per task = 20,000 records/sec
- Accounting for network latency: 15,000-20,000 records/sec

---

## 🚀 Complete Command Example

### For Your 12-Core Machine (Local Mode):

```bash
spark-submit \
  --master local[10] \
  --driver-memory 8G \
  --executor-memory 8G \
  --conf spark.default.parallelism=40 \
  --conf spark.sql.shuffle.partitions=40 \
  --conf spark.executor.memoryOverhead=2G \
  --conf spark.network.timeout=600s \
  --conf spark.executor.heartbeatInterval=60s \
  --conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
  --conf spark.sql.adaptive.enabled=true \
  --conf spark.sql.adaptive.coalescePartitions.enabled=true \
  --properties-file transaction-test-audit.properties \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

**Key Points:**
- `local[10]` = Use 10 of 12 cores (leave 2 for OS)
- `parallelism=40` = 4x cores (allows 40 parallel tasks)
- `executor-memory=8G` = Enough for batching
- `memoryOverhead=2G` = Extra memory for JVM overhead

---

## 🔍 Verification Steps

### 1. Check Spark UI (if available)

After starting migration, check Spark UI at `http://your-spark-master:4040`:

- **Active Tasks:** Should show 20-40 tasks running in parallel
- **Executor Memory:** Should show 8G per executor
- **Executor Cores:** Should show 4-8 cores per executor

### 2. Check Logs

```bash
# Should show multiple partitions processing simultaneously
grep "Processing min:" migration_logs/*.log | head -20

# Should show high throughput
grep "Throughput:" migration_logs/migration_summary_*.txt
```

### 3. Monitor Performance

```bash
# Watch real-time throughput
tail -f migration_logs/migration_summary_*.txt | grep "Throughput:"

# Should see: Throughput: 15000.00 records/sec (or higher)
```

---

## ⚠️ Multi-Region Considerations

For your future multi-region YugabyteDB setup:

1. **Enable Load Balancing:**
   ```properties
   spark.cdm.connect.target.yugabyte.loadBalance=true
   spark.cdm.connect.target.yugabyte.topologyKeys=azure.central.zone1,azure.central.zone2,azure.central.zone3
   ```

2. **Increase Connection Pool:**
   ```properties
   spark.cdm.connect.target.yugabyte.pool.maxSize=10  # More connections for multi-region
   ```

3. **Network Latency:**
   - Multi-region will have higher latency
   - Compensate with larger batch sizes (100-200)
   - Use more parallelism to maintain throughput

---

## 📋 Checklist for 20K IOPS

- [ ] **Spark parallelism = 40-80** (not 1!)
- [ ] **Executor cores = 4-8** (not 1!)
- [ ] **Executor memory = 8G** (not 1g!)
- [ ] **num-executors = 5-10** (not 1!)
- [ ] **CDM numParts = 20-40** (matches Spark parallelism)
- [ ] **Batch size = 50-100** (larger for network latency)
- [ ] **Fetch size = 1000-2000** (larger for network latency)
- [ ] **Rate limits removed or set to 50000+**
- [ ] **Connection pool = numParts/8** (5-10 connections)
- [ ] **Network latency accounted for** (larger batches)

---

## 🎯 Summary

**Root Cause:** Spark configuration, not CDM settings.

**Primary Fixes:**
1. **Set `spark.default.parallelism=40-80`** (currently 1)
2. **Set `--executor-cores 4`** (currently 1)
3. **Set `--executor-memory 8G`** (currently 1g)
4. **Set `--num-executors 5`** or use `local[10]` (currently 1)

**Expected Result:** 15,000-25,000 records/sec (target: 20,000)

**For Multi-Region:** Increase batch size to 100-200 to compensate for network latency.

