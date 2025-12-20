# Property Verification - Are Properties Actually Being Used?

## Critical Question
**How do we know batch size, fetch size, and other properties are actually being used?**

## Verification Method

### 1. Property Loading Verification ✅

From migration logs, we can see properties ARE being loaded:

```
25/12/18 23:28:17 INFO PropertyHelper: Known property [spark.cdm.perfops.batchSize] is configured with value [50] and is type [NUMBER]
25/12/18 23:28:17 INFO PropertyHelper: Known property [spark.cdm.connect.target.yugabyte.batchSize] is configured with value [100] and is type [NUMBER]
25/12/18 23:28:17 INFO PropertyHelper: Known property [spark.cdm.perfops.fetchSizeInRows] is configured with value [5000] and is type [NUMBER]
```

**Status:** ✅ Properties are loaded from file

---

### 2. Batch Size Usage Verification ✅

#### YugabyteDB Batch Size (JDBC Batching)
**Property:** `spark.cdm.connect.target.yugabyte.batchSize`
**Used in:** `YugabyteUpsertStatement.java:97-98`

```java
Number configuredBatchSize = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_BATCH_SIZE);
this.batchSize = (configuredBatchSize != null) ? configuredBatchSize.intValue() : 25;
```

**Logged at:** `YugabyteUpsertStatement.java:109`
```
25/12/18 23:28:19 INFO YugabyteUpsertStatement:   Batch Size: 100 records per batch
```

**Status:** ✅ **CONFIRMED** - Batch size 100 is being used

#### CDM Batch Size (Internal)
**Property:** `spark.cdm.perfops.batchSize`
**Used in:** `CqlTable.java:202-208`

**Note:** This is for CQL batch operations, NOT YugabyteDB JDBC batching.

---

### 3. Fetch Size Usage Verification ⚠️ NEEDS VERIFICATION

**Property:** `spark.cdm.perfops.fetchSizeInRows`
**Read in:** `CqlTable.java:198-200`

```java
public Integer getFetchSizeInRows() {
    return propertyHelper.getInteger(KnownProperties.PERF_FETCH_SIZE);
}
```

**Used in:** `YugabyteCopyJobSession.java:89`
```java
fetchSize = this.originSession.getCqlTable().getFetchSizeInRows();
```

**Logged at:** `YugabyteCopyJobSession.java:104`
```
25/12/18 23:28:19 INFO YugabyteCopyJobSession:   Fetch Size: 5000 rows
```

**BUT:** Need to verify this is actually applied to Cassandra ResultSet!

**Status:** ⚠️ **NEEDS VERIFICATION** - Logged but need to confirm it's applied to queries

---

### 4. Number of Partitions Verification ✅

**Property:** `spark.cdm.perfops.numParts`
**Used in:** Spark partition splitting

**Verification:** Check actual partitions created:
- Logs show: "Partitions: 61/80" or "40/40"
- This confirms numParts is being used

**Status:** ✅ **CONFIRMED** - Partitions match configuration

---

## Potential Issues

### Issue 1: Fetch Size May Not Be Applied

**Problem:** Fetch size is read and logged, but we need to verify it's actually set on Cassandra ResultSet.

**Check:** Look for `resultSet.setFetchSize()` or similar in query execution code.

**Location to Check:**
- `OriginSelectByPartitionRangeStatement.java`
- Any ResultSet execution code

### Issue 2: Two Different Batch Sizes

**Confusion:** There are TWO batch size properties:

1. **`spark.cdm.connect.target.yugabyte.batchSize`** (100)
   - Used for: YugabyteDB JDBC batching
   - Location: `YugabyteUpsertStatement.java`
   - **Status:** ✅ Confirmed in use

2. **`spark.cdm.perfops.batchSize`** (50)
   - Used for: CDM internal CQL batching
   - Location: `CqlTable.java`
   - **Status:** ⚠️ May not be used for YugabyteDB migration

---

## Verification Commands

### Check Property Loading:
```bash
grep "Known property.*batchSize\|fetchSize\|numParts" migration_*.log
```

### Check Actual Values Used:
```bash
grep "Batch Size:\|Fetch Size:\|Partitions:" migration_*.log
```

### Check if Fetch Size is Applied:
```bash
grep -r "setFetchSize\|fetchSize" src/main/java/com/datastax/cdm/
```

---

## Recommendations

1. **Add explicit logging** when fetch size is applied to ResultSet
2. **Verify** fetch size is actually set on Cassandra queries
3. **Clarify** the difference between the two batch size properties
4. **Add runtime verification** that properties match configuration

