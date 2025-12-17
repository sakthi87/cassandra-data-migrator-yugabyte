# Quick Connection Diagnosis Guide

## How to Find Number of Connections CDM Tried

### Quick Answer

**Your Configuration** (when it failed):
- Executors: 4 instances × 4 cores = 16 parallel tasks
- `connections_per_executor_local`: **NOT SET** (defaults to **1**)
- **Result**: 4 × 4 × 1 = **16 connections total**

**But with numParts=5000**, you need **64-96 connections** → **Connection exhaustion!**

---

## Step-by-Step Diagnosis

### Step 1: Check Your Properties File

```bash
grep "connections_per_executor_local" your-properties-file.properties
```

**If not found**: That's the problem! Default is 1, which is too low.

### Step 2: Calculate Expected Connections

```bash
# Run the analysis script
./analyze_connections.sh
```

This will:
- Analyze your logs for connection errors
- Calculate expected connections from your configuration
- Show recommended connection settings

### Step 3: Check Logs for Connection Errors

```bash
# Quick check
grep -i "no connection\|pool exhausted" migration_logs/*.log | wc -l

# Detailed check
grep -i "no connection\|pool exhausted\|connection.*timeout" migration_logs/*.log
```

### Step 4: Monitor During Next Run

```bash
# Run in separate terminal
./monitor_connections.sh
```

This monitors connection errors in real-time.

---

## Understanding dsbulk vs CDM

### dsbulk Performance
- **Throughput**: 17,000 rows/sec (with batches=26)
- **Batches=26**: This means dsbulk uses **26 concurrent connections/threads**
- **Why it worked**: dsbulk has optimized connection pooling for high throughput

### CDM Performance (when it failed)
- **Throughput**: Failed due to connection exhaustion
- **Connections**: Only 16 connections (4 executors × 4 cores × 1 connection)
- **Why it failed**: Too few connections for high parallelism (numParts=5000)

### Comparison

| Tool | Connections | Throughput | Status |
|------|-------------|------------|--------|
| dsbulk | 26 | 17k rows/sec | ✅ Works |
| CDM (default) | 16 | Failed | ❌ Exhausted |
| CDM (optimized) | 64 | Expected: 10k+ rows/sec | ✅ Should work |

---

## Quick Fix

Add to your properties file:

```properties
# CRITICAL: Increase connections per executor
spark.cassandra.connection.connections_per_executor_local=4
```

**Recalculation**:
- 4 executors × 4 cores × 4 connections = **64 connections**
- This should handle numParts=5000

---

## Connection Requirements by numParts

| numParts | Recommended connections_per_executor_local | Total Connections | Status |
|----------|--------------------------------------------|-------------------|--------|
| 500 | 2 | 32 | ✅ OK |
| 1000 | 2-3 | 32-48 | ✅ OK |
| 2000 | 3 | 48 | ✅ OK |
| 5000 | 4 | 64 | ✅ OK |
| 10000+ | 4-5 | 64-80 | ⚠️ Check YugabyteDB limits |

---

## What "batches=26" Means in dsbulk

In dsbulk, `batches=26` means:
- **26 concurrent batches** of data being processed
- Each batch uses **1 connection**
- **Total**: 26 connections to YugabyteDB
- This is similar to CDM's `connections_per_executor_local × executors × cores`

**CDM Equivalent**:
- To match dsbulk's 26 connections, you'd need:
  - 4 executors × 4 cores × 2 connections = 32 connections (close to 26)
  - Or: 2 executors × 4 cores × 3 connections = 24 connections (closer to 26)

---

## Expected Connections Calculation

### Formula

```
Total Connections = Executor Instances × Executor Cores × Connections Per Executor
```

### Your Case

**When it failed**:
```
4 executors × 4 cores × 1 connection (default) = 16 connections
```

**With fix**:
```
4 executors × 4 cores × 4 connections = 64 connections
```

**For numParts=5000**:
- Need: ~64-96 connections
- Have (with fix): 64 connections
- Status: ✅ Should work (may need to increase to 5 if still issues)

---

## Diagnostic Commands

### 1. Check if setting exists
```bash
grep "connections_per_executor_local" your-properties-file.properties
```

### 2. Calculate from properties
```bash
EXEC_INSTANCES=$(grep "^spark.executor.instances" your-properties-file.properties | cut -d'=' -f2)
EXEC_CORES=$(grep "^spark.executor.cores" your-properties-file.properties | cut -d'=' -f2)
CONN_PER_EXEC=$(grep "^spark.cassandra.connection.connections_per_executor_local" your-properties-file.properties | cut -d'=' -f2)
if [ -z "$CONN_PER_EXEC" ]; then CONN_PER_EXEC=1; fi
TOTAL=$((EXEC_INSTANCES * EXEC_CORES * CONN_PER_EXEC))
echo "Total Connections: $TOTAL"
```

### 3. Analyze logs
```bash
./analyze_connections.sh
```

### 4. Monitor in real-time
```bash
./monitor_connections.sh
```

---

## Summary

**Your Issue**:
- CDM tried to use **16 connections** (default)
- Needed **64-96 connections** for numParts=5000
- **Result**: Connection exhaustion

**Solution**:
- Set `spark.cassandra.connection.connections_per_executor_local=4`
- This gives you **64 connections** (should be sufficient)

**Why dsbulk worked**:
- dsbulk uses **26 connections** (batches=26)
- This is more than CDM's default 16, but less than what CDM needs for high parallelism
- dsbulk has better connection management for its use case

**Next Steps**:
1. Add `connections_per_executor_local=4` to properties file
2. Retry migration
3. If still fails, check YugabyteDB connection limits
4. Use `./analyze_connections.sh` to diagnose further

