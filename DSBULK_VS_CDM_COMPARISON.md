# dsbulk vs CDM: Comprehensive Comparison

## Executive Summary

**dsbulk**: Purpose-built bulk loading tool optimized for high-throughput data migration. Achieves **17K rows/sec** in your environment.

**CDM**: General-purpose migration tool built on Spark, designed for data validation, transformation, and migration. Struggles to reach **8K rows/sec** in your environment.

---

## Architecture Comparison

### dsbulk Architecture

```
dsbulk Process
├── Direct DataStax Java Driver Connection
├── Optimized Connection Pool (18 connections)
├── True Async/Non-blocking Writes
├── Efficient Batch Processing (batches=25)
└── Purpose-built for Bulk Loading
```

**Key Characteristics:**
- **Lightweight**: Single-process, no Spark overhead
- **Direct Driver**: Uses DataStax Java Driver directly
- **Optimized Pool**: Custom connection pool management
- **True Async**: Non-blocking I/O throughout
- **Purpose-built**: Designed specifically for bulk loading

### CDM Architecture

```
CDM Process
├── Spark Framework
│   ├── Spark Driver
│   ├── Spark Executors (in local mode: 1 executor)
│   └── Spark Cassandra Connector
│       └── DataStax Java Driver
├── Connection Pool (via Spark Connector)
├── Blocking Async Writes (bottleneck!)
└── General-purpose Migration Tool
```

**Key Characteristics:**
- **Spark-based**: Built on Apache Spark framework
- **Heavyweight**: Spark overhead (driver, executors, serialization)
- **Indirect Driver**: Uses Spark Cassandra Connector wrapper
- **Blocking Async**: Uses async but blocks with `.join()`
- **General-purpose**: Designed for migration, validation, transformation

---

## Performance Comparison

### Your Environment Results

| Metric | dsbulk | CDM | Difference |
|--------|--------|-----|------------|
| **Throughput** | 17,000 rows/sec | 2,000-8,000 rows/sec | **2-8x slower** |
| **Connections** | 18 connections | 3-9 connections | **2-6x fewer** |
| **Latency (p99)** | Low | 45ms (high) | **Much higher** |
| **CPU Usage** | Efficient | High (100% on YugabyteDB) | **Less efficient** |
| **Connection Issues** | None | Frequent ("no connection available") | **Many issues** |

### Performance Factors

#### 1. Async Write Implementation

**dsbulk:**
```java
// True non-blocking async
executeAsync(batch)
  .thenCompose(result -> processNext())
  .thenCompose(result -> processNext())
// No blocking waits
```

**CDM:**
```java
// Pseudo-async (blocks!)
writeResults.stream().forEach(
  writeResult -> writeResult.toCompletableFuture().join().one()
);
// Blocks sequentially on each result!
```

**Impact:** CDM's blocking async defeats the purpose of async operations, creating pipeline stalls.

#### 2. Connection Pool Management

**dsbulk:**
- Direct connection pool control
- Optimized for bulk loading
- Better connection reuse
- Handles cross-region connections well

**CDM:**
- Spark Cassandra Connector manages pool
- Generic connection pool (not optimized for bulk loading)
- Connection pool shared across Spark tasks
- Struggles with cross-region (on-prem → Azure)

**Impact:** dsbulk's optimized pool handles 18 connections efficiently; CDM struggles with fewer connections.

#### 3. Rate Limiting

**dsbulk:**
- Rate limiting at batch/thread level
- Less overhead per operation
- Better throughput

**CDM:**
- Rate limiting per operation (`rateLimiter.acquire(1)` for each write)
- Blocks thread for each operation
- Adds latency to every write

**Impact:** CDM's per-operation rate limiting adds significant overhead.

#### 4. Pipelining

**dsbulk:**
- Continuous read-write pipelining
- No blocking waits
- Overlaps I/O operations

**CDM:**
- Blocks on flush operations
- Waits for all writes to complete before continuing
- Less effective pipelining

**Impact:** dsbulk maintains continuous throughput; CDM has pipeline stalls.

---

## Connection Handling Comparison

### dsbulk Connection Behavior

**Your Environment:**
- **Connections**: 18 total (optimized pool)
- **Routing**: Efficiently routes to preferred regions (Azure Central/East)
- **Cross-region**: Handles on-prem → Azure connections well
- **Follower nodes**: Ignores/avoids on-prem follower node
- **Result**: Stable, high-throughput connections

**Key Features:**
- Direct connection pool management
- Better topology awareness
- Efficient connection reuse
- Handles stretch clusters well

### CDM Connection Behavior

**Your Environment:**
- **Connections**: 3-9 total (limited by configuration)
- **Routing**: Without `local_dc`, connection pool is unoptimized
- **Cross-region**: Struggles with on-prem → Azure connections
- **Follower nodes**: May try to connect to on-prem follower
- **Result**: Connection exhaustion, "no connection available" errors

**Key Issues:**
- Spark Cassandra Connector's generic pool
- Requires explicit `local_dc` configuration
- Connection pool shared across Spark tasks
- Less efficient for cross-region scenarios

**Critical Missing Configuration:**
```properties
# CDM needs this (dsbulk handles it automatically):
spark.cassandra.connection.local_dc=azcentral
```

---

## Use Case Comparison

### When to Use dsbulk

✅ **Best For:**
- **Bulk data loading** (primary use case)
- **High-throughput migrations** (10K+ rows/sec)
- **Simple data copy** (no transformations)
- **Time-sensitive migrations**
- **Cross-region migrations** (stretch clusters)
- **Single-node execution** (no Spark cluster needed)

❌ **Not Ideal For:**
- Complex data transformations
- Data validation during migration
- Schema evolution
- Incremental migrations with tracking

### When to Use CDM

✅ **Best For:**
- **Data validation** (compare source vs target)
- **Complex transformations** (column mapping, TTL, writetime)
- **Schema evolution** (different schemas)
- **Incremental migrations** (track run IDs)
- **Multi-table migrations** (batch processing)
- **Spark cluster environments** (distributed processing)

❌ **Not Ideal For:**
- Simple bulk loading (overkill)
- Maximum throughput requirements
- Single-node execution (Spark overhead)
- Cross-region stretch clusters (connection issues)

---

## Configuration Comparison

### dsbulk Configuration

**Simple, Direct:**
```bash
dsbulk load \
  -h <host> \
  -k <keyspace> \
  -t <table> \
  --batch.mode DISABLED \
  --executor.maxConcurrentQueries 25 \
  --executor.maxPerSecond 10000
```

**Key Settings:**
- `executor.maxConcurrentQueries=25` → 25 concurrent operations (batches)
- `executor.maxPerSecond=10000` → Rate limiting
- Connection pool managed automatically (18 connections)
- Topology awareness built-in

### CDM Configuration

**Complex, Many Parameters:**
```properties
# Spark Configuration
spark.executor.cores=4
spark.executor.instances=4
spark.executor.memory=30G
spark.driver.memory=30G

# CDM Performance
spark.cdm.perfops.numParts=2000
spark.cdm.perfops.ratelimit.origin=8000
spark.cdm.perfops.ratelimit.target=8000
spark.cdm.perfops.batchSize=2000
spark.cdm.perfops.fetchSizeInRows=5000

# Connection Pool (CRITICAL!)
spark.cassandra.connection.local_dc=azcentral  # Must set explicitly!
spark.cassandra.connection.localConnectionsPerExecutor=8
spark.cassandra.connection.remoteConnectionsPerExecutor=1
spark.cassandra.connection.timeoutMS=120000
```

**Key Settings:**
- Many parameters to tune
- Must explicitly configure connection pool
- Must set `local_dc` for stretch clusters
- Spark overhead adds complexity

---

## Why dsbulk Works Better in Your Environment

### 1. Stretch Cluster Handling

**Your Setup:**
- YugabyteDB: Azure East (2 nodes), Azure Central (2 nodes), On-prem (1 follower)
- Preferred regions: Azure Central and Azure East only
- CDM: On-prem → Azure Central (cross-region)

**dsbulk:**
- Automatically handles preferred regions
- Efficiently routes to Azure Central/East
- Ignores on-prem follower
- Handles cross-region connections well

**CDM:**
- Requires explicit `local_dc=azcentral` configuration
- Without it, connection pool is unoptimized
- May try to connect to on-prem follower
- Struggles with cross-region latency

### 2. Connection Pool Efficiency

**dsbulk:**
- 18 connections → 17K rows/sec
- Efficient connection reuse
- Optimized for bulk loading

**CDM:**
- 3-9 connections → 2-8K rows/sec
- Less efficient connection reuse
- Generic connection pool (not optimized for bulk loading)

### 3. Async Implementation

**dsbulk:**
- True non-blocking async
- No pipeline stalls
- Continuous throughput

**CDM:**
- Blocking async (`.join()` calls)
- Pipeline stalls
- Lower throughput

### 4. Overhead

**dsbulk:**
- Minimal overhead
- Direct driver usage
- Single-process execution

**CDM:**
- Spark framework overhead
- Serialization overhead
- Multiple layers (Spark → Connector → Driver)

---

## Performance Bottlenecks in CDM

### 1. Blocking Async Writes (Critical)

**Location:** `CopyJobSession.java:142`
```java
writeResults.stream().forEach(
  writeResult -> writeResult.toCompletableFuture().join().one()
);
```

**Problem:** Blocks sequentially on each async result, defeating async benefits.

**Impact:** Major bottleneck, reduces throughput significantly.

### 2. Per-Operation Rate Limiting

**Location:** `CopyJobSession.java:100`
```java
rateLimiterTarget.acquire(1);  // Blocks for each operation
```

**Problem:** Rate limiter blocks thread for every single write operation.

**Impact:** Adds latency to every operation, reduces throughput.

### 3. Connection Pool Configuration

**Problem:** Requires explicit configuration, defaults are too low.

**Impact:** Connection exhaustion, "no connection available" errors.

### 4. Spark Overhead

**Problem:** Spark framework adds overhead for simple bulk loading.

**Impact:** Lower efficiency compared to purpose-built tools.

---

## Recommendations

### Use dsbulk When:
- ✅ Simple bulk data loading
- ✅ Maximum throughput required (10K+ rows/sec)
- ✅ Cross-region migrations (stretch clusters)
- ✅ Single-node execution
- ✅ Time-sensitive migrations

### Use CDM When:
- ✅ Data validation needed
- ✅ Complex transformations required
- ✅ Schema evolution needed
- ✅ Incremental migrations with tracking
- ✅ Spark cluster available

### For Your Use Case:

**Current Situation:**
- Simple bulk loading (DataStax → YugabyteDB)
- High throughput required (17K rows/sec)
- Cross-region (on-prem → Azure)
- Stretch cluster (preferred regions)

**Recommendation:** **Use dsbulk** for this migration.

**If You Must Use CDM:**
1. Set `spark.cassandra.connection.local_dc=azcentral` (critical!)
2. Increase connections: `localConnectionsPerExecutor=8`
3. Increase timeouts: `timeoutMS=120000`
4. Reduce rate limits initially: `ratelimit.target=5000`
5. Monitor connection usage in YBA UI

---

## Summary Table

| Aspect | dsbulk | CDM |
|--------|--------|-----|
| **Purpose** | Bulk loading | General migration |
| **Architecture** | Lightweight, direct | Spark-based, layered |
| **Throughput** | 17K rows/sec ✅ | 2-8K rows/sec ⚠️ |
| **Connections** | 18 (optimized) | 3-9 (requires tuning) |
| **Async Writes** | True non-blocking ✅ | Blocking async ❌ |
| **Connection Pool** | Optimized ✅ | Generic, requires config ⚠️ |
| **Stretch Clusters** | Handles well ✅ | Requires explicit config ⚠️ |
| **Configuration** | Simple ✅ | Complex ⚠️ |
| **Overhead** | Minimal ✅ | Spark overhead ⚠️ |
| **Best For** | Bulk loading | Validation, transformations |
| **Your Result** | 17K rows/sec ✅ | 2-8K rows/sec ⚠️ |

---

## Conclusion

**dsbulk** is purpose-built for bulk loading and achieves **2-8x better performance** than CDM in your environment due to:

1. **True async implementation** (vs CDM's blocking async)
2. **Optimized connection pool** (vs CDM's generic pool)
3. **Better stretch cluster handling** (vs CDM requiring explicit config)
4. **Minimal overhead** (vs CDM's Spark overhead)
5. **Purpose-built design** (vs CDM's general-purpose design)

**CDM** is better suited for scenarios requiring:
- Data validation
- Complex transformations
- Schema evolution
- Incremental migrations

For simple bulk loading with maximum throughput, **dsbulk is the clear winner**.

