# Performance Troubleshooting Guide

## Current Issue: Low Throughput (130k records in 6 minutes)

**Current Performance:** ~361 records/sec  
**Expected Performance:** 15,000-20,000 records/sec  
**Performance Gap:** ~2.4% of target (98% slower than expected)

---

## 🔍 Quick Diagnosis Steps

### Step 1: Check Current Configuration

Verify these critical settings in your properties file:

```bash
# Check key performance settings
grep -E "numParts|batchSize|pool.maxSize|fetchSize" your-properties-file.properties
```

**Expected values:**
- `spark.cdm.perfops.numParts` = **20-50** (parallelism)
- `spark.cdm.connect.target.yugabyte.batchSize` = **25-100** (batch size)
- `spark.cdm.connect.target.yugabyte.pool.maxSize` = **5-10** (connection pool)
- `spark.cdm.perfops.fetchSizeInRows` = **500-1000** (fetch size)

### Step 2: Check Migration Logs

```bash
# Check for errors or warnings
grep -i "error\|warn\|exception" migration_logs/*.log | tail -20

# Check current throughput in summary file
tail -20 migration_logs/migration_summary_*.txt

# Check Spark executor status
grep -i "executor\|partition\|batch" migration_logs/*.log | tail -20
```

### Step 3: Check Resource Utilization

```bash
# Check if Spark executors are running
# (In Spark UI or logs, check executor count)

# Check YugabyteDB connection count
# (Connect to YugabyteDB and run:)
# SELECT count(*) FROM pg_stat_activity WHERE datname = 'your_database';
```

---

## 🚨 Common Causes of Low Performance

### 1. **Insufficient Parallelism (numParts too low)**

**Symptom:** Only a few partitions processing at once

**Check:**
```properties
spark.cdm.perfops.numParts=10  # ❌ Too low
```

**Fix:**
```properties
spark.cdm.perfops.numParts=20  # ✅ Better
# or
spark.cdm.perfops.numParts=50  # ✅ For large datasets
```

**Impact:** Each partition processes data in parallel. More partitions = more parallelism.

---

### 2. **Small Batch Size**

**Symptom:** Many small batches, high overhead

**Check:**
```properties
spark.cdm.connect.target.yugabyte.batchSize=5  # ❌ Too small
```

**Fix:**
```properties
spark.cdm.connect.target.yugabyte.batchSize=25  # ✅ Minimum
# or
spark.cdm.connect.target.yugabyte.batchSize=50  # ✅ Better
# or
spark.cdm.connect.target.yugabyte.batchSize=100 # ✅ Best (if memory allows)
```

**Impact:** Larger batches = fewer round trips = higher throughput.

---

### 3. **Connection Pool Too Small**

**Symptom:** Connection wait times, "too many clients" errors

**Check:**
```properties
spark.cdm.connect.target.yugabyte.pool.maxSize=1  # ❌ Too small
```

**Fix:**
```properties
spark.cdm.connect.target.yugabyte.pool.maxSize=5  # ✅ Minimum
# or
spark.cdm.connect.target.yugabyte.pool.maxSize=10 # ✅ Better
```

**Note:** Don't set too high! Formula: `maxSize = numParts / 4` (e.g., 20 partitions = 5 maxSize)

**Impact:** More connections = more parallel writes.

---

### 4. **Rate Limiting Too Restrictive**

**Symptom:** Artificial throttling

**Check:**
```properties
spark.cdm.perfops.targetRate=100  # ❌ Too low (100 ops/sec)
```

**Fix:**
```properties
spark.cdm.perfops.targetRate=20000  # ✅ High rate
# or remove rate limiting entirely for maximum speed
# (comment out the property)
```

**Impact:** Rate limiting caps throughput. Remove or increase for maximum speed.

---

### 5. **Network Latency or Connectivity Issues**

**Symptom:** Slow connection to YugabyteDB

**Check:**
```bash
# Test network latency to YugabyteDB
ping yugabyte-host
# or
telnet yugabyte-host 5433
```

**Fix:**
- Ensure Spark and YugabyteDB are on same network/low latency
- Check firewall rules
- Verify YugabyteDB is not overloaded

---

### 6. **YugabyteDB Resource Constraints**

**Symptom:** YugabyteDB CPU/memory/disk at 100%

**Check:**
```bash
# Connect to YugabyteDB and check:
# - CPU usage
# - Memory usage
# - Disk I/O
# - Active connections
```

**Fix:**
- Scale up YugabyteDB resources
- Reduce parallelism temporarily
- Check for other concurrent workloads

---

### 7. **Spark Executor Resources**

**Symptom:** Spark executors running out of memory or CPU

**Check Spark UI or logs for:**
- Executor memory usage
- GC (Garbage Collection) frequency
- Task failures

**Fix:**
```bash
# Increase executor memory
--executor-memory 4G  # or 8G for large datasets

# Increase driver memory
--driver-memory 4G
```

---

### 8. **Fetch Size Too Small**

**Symptom:** Many small reads from Cassandra

**Check:**
```properties
spark.cdm.perfops.fetchSizeInRows=100  # ❌ Too small
```

**Fix:**
```properties
spark.cdm.perfops.fetchSizeInRows=500  # ✅ Better
# or
spark.cdm.perfops.fetchSizeInRows=1000 # ✅ Best
```

**Impact:** Larger fetch size = fewer round trips to Cassandra.

---

## 🔧 Recommended Configuration for High Performance

Based on your current issue (130k in 6 min = 361 records/sec), here's an optimized configuration:

```properties
# ========================================================================
# PERFORMANCE SETTINGS - OPTIMIZED FOR HIGH THROUGHPUT
# ========================================================================

# Parallelism (number of Spark partitions)
# Higher = more parallelism, but don't exceed available cores
spark.cdm.perfops.numParts=20

# Batch size for YugabyteDB writes
# Larger = fewer round trips, but more memory per batch
spark.cdm.connect.target.yugabyte.batchSize=50

# Connection pool size
# Formula: maxSize = numParts / 4 (e.g., 20 partitions = 5 maxSize)
spark.cdm.connect.target.yugabyte.pool.maxSize=5

# Fetch size from Cassandra
# Larger = fewer round trips to Cassandra
spark.cdm.perfops.fetchSizeInRows=1000

# Rate limiting (remove or set very high for maximum speed)
# spark.cdm.perfops.targetRate=20000

# YugabyteDB Smart Driver optimizations
spark.cdm.connect.target.yugabyte.rewriteBatchedInserts=true
spark.cdm.connect.target.yugabyte.prepareThreshold=5
spark.cdm.connect.target.yugabyte.tcpKeepAlive=true
```

---

## 📊 Performance Calculation

**Current:**
- 130,000 records in 6 minutes (360 seconds)
- Throughput: ~361 records/sec

**Target:**
- 15,000-20,000 records/sec

**To achieve target, you need:**
- **41x improvement** (15,000 / 361)
- Or **55x improvement** (20,000 / 361)

**Estimated time for 6.4M records at current rate:**
- 6,400,000 / 361 = **17,728 seconds = ~4.9 hours** ❌
- **Target:** 6,400,000 / 15,000 = **427 seconds = ~7 minutes** ✅

---

## 🔍 Diagnostic Commands for Your Environment

### Check Current Throughput

```bash
# If you have access to the summary file
tail -f migration_logs/migration_summary_*.txt | grep "Throughput:"

# Or calculate from records written
# (Check target table count periodically)
```

### Check Configuration

```bash
# Verify all performance settings are set
grep -E "numParts|batchSize|pool.maxSize|fetchSize|targetRate" your-properties-file.properties
```

### Check for Errors

```bash
# Look for errors in logs
grep -i "error\|exception\|failed" migration_logs/*.log | tail -50

# Check for connection issues
grep -i "connection\|timeout\|refused" migration_logs/*.log
```

### Check Spark Status

```bash
# If you have Spark UI access, check:
# - Number of active executors
# - Tasks running/completed
# - Shuffle read/write
# - GC time
```

---

## 🎯 Quick Fix Checklist

Run through this checklist in order:

- [ ] **1. Verify `numParts` is 20-50** (not 1-5)
- [ ] **2. Verify `batchSize` is 25-100** (not 1-10)
- [ ] **3. Verify `pool.maxSize` is 5-10** (not 1-2)
- [ ] **4. Verify `fetchSizeInRows` is 500-1000** (not 100)
- [ ] **5. Check if `targetRate` is set too low** (remove or set to 20000)
- [ ] **6. Check for errors in logs** (connection errors, timeouts)
- [ ] **7. Verify Spark executors are running** (check executor count)
- [ ] **8. Check YugabyteDB is not overloaded** (CPU, memory, connections)
- [ ] **9. Verify network latency is low** (<10ms to YugabyteDB)
- [ ] **10. Check if rate limiting is too restrictive**

---

## 📝 What to Share for Further Diagnosis

If performance is still low after applying fixes, share:

1. **Configuration file** (with sensitive data redacted):
   ```bash
   grep -E "numParts|batchSize|pool|fetchSize|targetRate|yugabyte" your-properties-file.properties
   ```

2. **Recent log entries**:
   ```bash
   tail -100 migration_logs/migration_summary_*.txt
   ```

3. **Error messages** (if any):
   ```bash
   grep -i "error\|exception" migration_logs/*.log | tail -20
   ```

4. **Spark executor count** (from Spark UI or logs)

5. **YugabyteDB connection count**:
   ```sql
   SELECT count(*) FROM pg_stat_activity WHERE datname = 'your_database';
   ```

---

## 🚀 Expected Improvement After Fixes

After applying the recommended configuration:

**Before:** 361 records/sec  
**After:** 15,000-20,000 records/sec  
**Improvement:** 41-55x faster

**Time for 6.4M records:**
- **Before:** ~4.9 hours
- **After:** ~7 minutes

---

## ⚠️ Important Notes

1. **Don't set `numParts` too high** - Each partition needs resources. Start with 20, increase if needed.

2. **Don't set `pool.maxSize` too high** - YugabyteDB has connection limits. Formula: `maxSize = numParts / 4`

3. **Monitor YugabyteDB** - High parallelism can overwhelm the database if not scaled properly.

4. **Test incrementally** - Start with recommended values, then tune based on your environment.

5. **Network matters** - Low latency between Spark and YugabyteDB is critical for performance.

