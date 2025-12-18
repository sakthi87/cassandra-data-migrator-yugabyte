# 🚨 QUICK FIX: Low Performance (130k in 6 min)

## Problem Identified

Your `transaction-test-audit.properties` is configured for **small test (10 records)**, not production load!

**Current Settings (Bottleneck):**
```properties
spark.cdm.perfops.numParts=2          # ❌ TOO LOW! Only 2 partitions
spark.cdm.perfops.fetchSizeInRows=100 # ❌ TOO LOW! Small fetch size
```

**Current Performance:** ~361 records/sec (2.4% of target)  
**Target Performance:** 15,000-20,000 records/sec

---

## 🔧 IMMEDIATE FIX

Update these lines in your properties file:

### Change 1: Increase Parallelism (CRITICAL)
```properties
# BEFORE (line 66):
spark.cdm.perfops.numParts=2

# AFTER:
spark.cdm.perfops.numParts=20
```

**Impact:** 10x more parallelism = 10x faster (if other settings allow)

### Change 2: Increase Fetch Size
```properties
# BEFORE (line 70):
spark.cdm.perfops.fetchSizeInRows=100

# AFTER:
spark.cdm.perfops.fetchSizeInRows=1000
```

**Impact:** 10x larger fetches = fewer round trips to Cassandra

### Change 3: Increase Rate Limits (if set)
```properties
# BEFORE (lines 68-69):
spark.cdm.perfops.ratelimit.origin=10000
spark.cdm.perfops.ratelimit.target=10000

# AFTER (or remove to disable):
spark.cdm.perfops.ratelimit.origin=20000
spark.cdm.perfops.ratelimit.target=20000
```

---

## 📋 Complete Optimized Configuration

Replace lines 62-70 in your properties file with:

```properties
# =============================================================================
# PERFORMANCE SETTINGS - OPTIMIZED FOR PRODUCTION LOAD
# =============================================================================
# CRITICAL: These settings were optimized for small test (10 records)
# For production loads, increase parallelism and fetch size
spark.cdm.perfops.numParts=20
spark.cdm.perfops.batchSize=25
spark.cdm.perfops.ratelimit.origin=20000
spark.cdm.perfops.ratelimit.target=20000
spark.cdm.perfops.fetchSizeInRows=1000
```

---

## 🎯 Expected Improvement

**Before (current):**
- numParts=2 → Only 2 parallel workers
- fetchSize=100 → Small fetches
- **Throughput: ~361 records/sec**

**After (optimized):**
- numParts=20 → 20 parallel workers (10x)
- fetchSize=1000 → Large fetches (10x)
- **Expected Throughput: 15,000-20,000 records/sec**

**Improvement:** 41-55x faster!

**Time for 6.4M records:**
- **Before:** ~4.9 hours
- **After:** ~7 minutes

---

## ⚠️ Important Notes

1. **Connection Pool:** Your `pool.maxSize=5` is correct for 20 partitions
   - Formula: `maxSize = numParts / 4` = 20 / 4 = 5 ✅

2. **Batch Size:** Your `batchSize=25` is good, keep it

3. **If you get "too many clients" error:**
   - Reduce `numParts` to 10
   - Or reduce `pool.maxSize` to 3

4. **Monitor YugabyteDB:**
   - Check connection count
   - Check CPU/memory usage
   - Ensure it can handle 20 parallel connections

---

## 🚀 Quick Action Steps

1. **Stop the current migration** (if possible, or let it finish)

2. **Edit properties file:**
   ```bash
   # Change line 66: numParts from 2 to 20
   # Change line 70: fetchSizeInRows from 100 to 1000
   ```

3. **Restart migration** with updated configuration

4. **Monitor performance:**
   ```bash
   tail -f migration_logs/migration_summary_*.txt | grep "Throughput:"
   ```

5. **Expected result:**
   - Should see 15,000-20,000 records/sec in logs
   - 6.4M records should complete in ~7 minutes

---

## 📊 Verification

After applying fixes, check logs for:

```bash
# Should show high throughput
grep "Throughput:" migration_logs/migration_summary_*.txt
# Expected: Throughput: 15000.00 records/sec (or higher)

# Should show 20 partitions processing
grep "Partitions:" migration_logs/migration_summary_*.txt
# Expected: Partitions: 20/20
```

---

## 🔍 If Still Slow After Fix

Check these additional issues:

1. **Network latency** - Is Spark close to YugabyteDB?
2. **YugabyteDB resources** - Is database CPU/memory maxed out?
3. **Spark executors** - Are executors actually running?
4. **Connection errors** - Check logs for connection issues
5. **Rate limiting** - Remove or increase rate limits further

See `PERFORMANCE_TROUBLESHOOTING.md` for detailed diagnosis.

