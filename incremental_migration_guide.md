# Incremental Migration Guide for YugabyteDB CDM

## 🔄 How Incremental Updates Work

### **1. First Migration (Full Scan)**
```bash
# Initial migration - migrates ALL 13M rows
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.trackRun=true \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

**What happens:**
- Creates tracking tables: `cdm_run_info` and `cdm_run_details`
- Generates unique `run_id` (timestamp-based)
- Splits full token range into partitions
- Stores each partition in `cdm_run_details` with status `NOT_STARTED`
- Processes each partition and updates status to `PASS`/`FAIL`
- Records completion in `cdm_run_info`

### **2. Incremental Migration (Only Failed/New Partitions)**
```bash
# Incremental migration - only processes failed partitions from previous run
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.trackRun=true \
  --conf spark.cdm.prevRunId=<PREVIOUS_RUN_ID> \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

**What happens:**
- Reads `prevRunId` from previous migration
- Queries `cdm_run_details` for partitions with status:
  - `NOT_STARTED` (never processed)
  - `STARTED` (started but not completed)
  - `FAIL` (failed during processing)
  - `DIFF` (had differences)
- Only processes these "pending" partitions
- Skips already successful partitions

### **3. Custom Token Range Migration**
```bash
# Custom range migration - specific token ranges only
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.filter.cassandra.partition.min="-4611686018427387904" \
  --conf spark.cdm.filter.cassandra.partition.max="4611686018427387903" \
  --conf spark.cdm.trackRun=true \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## 📊 Tracking Table Status Values

### **Run Status (cdm_run_info.status):**
- `NOT_STARTED`: Run created but not started
- `STARTED`: Run in progress
- `ENDED`: Run completed successfully

### **Partition Status (cdm_run_details.status):**
- `NOT_STARTED`: Partition not yet processed
- `STARTED`: Partition processing started
- `PASS`: Partition processed successfully
- `FAIL`: Partition processing failed
- `DIFF`: Partition had differences (for diff jobs)
- `DIFF_CORRECTED`: Differences were corrected

## 🔍 Monitoring Your Migration

### **Check Run Status:**
```sql
-- Check all runs for customer table
SELECT * FROM your_keyspace.cdm_run_info 
WHERE table_name = 'customer' 
ORDER BY run_id DESC;

-- Check specific run details
SELECT * FROM your_keyspace.cdm_run_details 
WHERE table_name = 'customer' AND run_id = <RUN_ID>
ORDER BY token_min;
```

### **Find Failed Partitions:**
```sql
-- Find failed partitions from last run
SELECT token_min, token_max, status, run_info 
FROM your_keyspace.cdm_run_details 
WHERE table_name = 'customer' 
  AND run_id = <LAST_RUN_ID>
  AND status IN ('FAIL', 'NOT_STARTED', 'STARTED')
ORDER BY token_min;
```

## 🎯 Best Practices

### **1. Initial Migration Strategy:**
```bash
# Start with full migration
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.trackRun=true \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  &> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

### **2. Handle Failures:**
```bash
# Re-run only failed partitions
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.trackRun=true \
  --conf spark.cdm.prevRunId=<FAILED_RUN_ID> \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

### **3. Custom Range for Testing:**
```bash
# Test with small token range first
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.filter.cassandra.partition.min="-9223372036854775808" \
  --conf spark.cdm.filter.cassandra.partition.max="-9223372036854775800" \
  --conf spark.cdm.trackRun=true \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## 🚀 Migration Workflow

1. **Analyze Token Ranges**: Use the provided scripts to understand your data distribution
2. **Initial Migration**: Run full migration with `trackRun=true`
3. **Monitor Progress**: Check tracking tables for status
4. **Handle Failures**: Re-run failed partitions using `prevRunId`
5. **Incremental Updates**: Use custom ranges for new data or specific partitions
6. **Validation**: Compare row counts and data integrity

## 📝 Example Migration Log

```
### YugabyteDB Migration - Starting ###
PARAM -- Min Partition: -9223372036854775808
PARAM -- Max Partition: 9223372036854775807
PARAM -- Number of Splits: 64
PARAM -- Track Run: true
PARAM -- RunId: 1704067200000000000
PARAM -- Coverage Percent: 100
PARAM Calculated -- Total Partitions: 64
###################### Run Id for this job is: 1704067200000000000 ######################
ThreadID: 1 Processing min: -9223372036854775808 max: -9223372036854775807
ThreadID: 2 Processing min: -9223372036854775806 max: -9223372036854775805
...
### YugabyteDB Migration - Stopped ###
```
