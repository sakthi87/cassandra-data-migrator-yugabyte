# Simple Steps with Commands - CDM Migration Guide

This guide provides simple step-by-step instructions to run Cassandra to YugabyteDB migration using CDM.

## Prerequisites

1. **Apache Spark** installed (via Homebrew: `brew install apache-spark`)
2. **Cassandra** running and accessible
3. **YugabyteDB** running and accessible
4. **JAR file** built: `target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar`

## Step 1: Build the Project

```bash
cd /path/to/cassandra-data-migrator-main
mvn clean package -DskipTests
```

This creates the JAR file: `target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar`

## Step 2: Configure Properties

Edit `transaction-test.properties` (or create your own) with your connection details:

```properties
# Cassandra Connection
spark.cdm.connect.origin.host=localhost
spark.cdm.connect.origin.port=9043
spark.cdm.connect.origin.username=cassandra
spark.cdm.connect.origin.password=cassandra

# YugabyteDB Connection
spark.cdm.connect.target.yugabyte.host=localhost
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=transaction_datastore
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=yugabyte

# Schema Configuration (Optional - defaults to "public")
# Only needed if your tables are in a custom schema
# spark.cdm.connect.target.yugabyte.schema=my_custom_schema

# Table Configuration
spark.cdm.schema.origin.keyspaceTable=transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr
spark.cdm.schema.target.keyspaceTable=transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr

# =============================================================================
# OPTIONAL: Audit Fields Population (Constant Columns Feature)
# =============================================================================
# If your target table has extra audit fields that don't exist in source,
# you can populate them with constant values during migration
# spark.cdm.feature.constantColumns.names=z_audit_crtd_by_txt,z_audit_evnt_id,z_audit_crtd_ts,z_audit_last_mdfd_by_txt
# spark.cdm.feature.constantColumns.values='CDM_MIGRATION','MIGRATION_BATCH_001','2024-12-17T10:00:00Z','CDM_MIGRATION'
```

## Step 3: Run Migration

### Option 1: Foreground Execution (Recommended for First Run)

**Best for:** Monitoring progress, debugging, small datasets

```bash
SPARK_HOME=$(brew --prefix apache-spark) && $SPARK_HOME/bin/spark-submit \
  --properties-file transaction-test.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr" \
  --master "local[*]" \
  --driver-memory 4G \
  --executor-memory 4G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

**What this does:**
- Runs migration in foreground so you can see progress
- Uses all available CPU cores (`local[*]`)
- Allocates 4GB memory for driver and executor
- Outputs logs to console

### Option 2: Background Execution (Recommended for Large Migrations)

**Best for:** Large datasets (100k+ records), long-running migrations

```bash
SPARK_HOME=$(brew --prefix apache-spark) && nohup $SPARK_HOME/bin/spark-submit \
  --properties-file transaction-test.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr" \
  --master "local[*]" \
  --driver-memory 4G \
  --executor-memory 4G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  > migration.log 2>&1 &
```

**What this does:**
- Runs migration in background
- Saves all output to `migration.log`
- Returns immediately so you can continue working
- Process continues even if you close terminal

**To monitor progress:**
```bash
tail -f migration.log | grep -E "(JobCounter|Final|records|ERROR)"
```

**To check if still running:**
```bash
ps aux | grep spark-submit
```

### Option 3: Using the Helper Script

**Best for:** Easy execution, consistent commands

```bash
# Foreground
./run_migration.sh foreground

# Background
./run_migration.sh background migration.log
```

## Step 3.5: Custom Schema Support (Optional)

> **✅ Feature Status**: Schema auto-detection is fully implemented and tested. CDM automatically detects the correct schema if the table is not found in the default "public" schema.

By default, CDM assumes YugabyteDB tables are in the `public` schema (PostgreSQL default). If your tables are in a custom schema, you have two options:

### Option 1: Using Configuration Property (Recommended)

Add the schema property to your properties file:

```properties
# Specify the schema name
spark.cdm.connect.target.yugabyte.schema=my_custom_schema
```

### Option 2: Using keyspaceTable Format

You can specify the schema directly in the `keyspaceTable` property:

```properties
# Format: schema.table
spark.cdm.schema.target.keyspaceTable=my_custom_schema.my_table

# Or: database.schema.table (database is already in connection URL)
spark.cdm.schema.target.keyspaceTable=transaction_datastore.my_custom_schema.my_table
```

### Auto-Detection Feature

CDM includes automatic schema detection:
- If the table is not found in the specified/default schema, CDM will automatically search for it across all schemas
- This is especially useful when migrating from Cassandra where the keyspace name might be confused with the schema name
- The detected schema is logged for visibility

**Example Log Output:**
```
INFO YugabyteTable: Discovering schema for table: transaction_datastore.my_table (database: transaction_datastore)
WARN YugabyteTable: Table my_table not found in schema transaction_datastore. Attempting to auto-detect schema...
INFO YugabyteTable: Found table my_table in schema public
INFO YugabyteTable: Auto-detected schema: public. Retrying table discovery...
```

### Common Scenarios

| Scenario | Configuration |
|----------|--------------|
| **Default public schema** | No configuration needed (default) |
| **Custom schema** | `spark.cdm.connect.target.yugabyte.schema=finance` |
| **Schema in keyspaceTable** | `spark.cdm.schema.target.keyspaceTable=finance.my_table` |
| **Auto-detection** | Let CDM find the schema automatically |

### Troubleshooting Schema Issues

**Issue: "Table not found in schema X"**
- CDM will automatically try to detect the correct schema
- Check the logs for auto-detection messages
- Manually specify the schema if auto-detection fails

**Issue: "Multiple tables with same name in different schemas"**
- Explicitly specify the schema using `spark.cdm.connect.target.yugabyte.schema`
- Or use the full path in keyspaceTable: `schema.table`

## Step 3.6: Audit Fields Population (Optional)

> **✅ Feature Status**: This feature is fully implemented and tested. It successfully populates audit fields for all migrated records.

If your target YugabyteDB table has extra audit fields that don't exist in the source Cassandra table, you can automatically populate them during migration using CDM's **Constant Columns** feature.

### Example: Populating Audit Fields

**Scenario:** Your target table has audit fields:
- `z_audit_crtd_by_txt` (TEXT) - Who created the record
- `z_audit_evnt_id` (TEXT) - Migration event ID
- `z_audit_crtd_ts` (TIMESTAMP) - When the record was created
- `z_audit_last_mdfd_by_txt` (TEXT) - Who last modified the record

**Configuration in `transaction-test.properties`:**
```properties
# Constant Columns Feature - Audit Fields
spark.cdm.feature.constantColumns.names=z_audit_crtd_by_txt,z_audit_evnt_id,z_audit_crtd_ts,z_audit_last_mdfd_by_txt
spark.cdm.feature.constantColumns.values='CDM_MIGRATION','MIGRATION_BATCH_001','2024-12-17T10:00:00Z','CDM_MIGRATION'
# Optional: If values contain commas, use a custom delimiter
# spark.cdm.feature.constantColumns.splitRegex=\\|
```

**What happens:**
- Every migrated record will have these audit fields populated with the specified constant values
- The values are the same for all records in the migration batch
- Useful for tracking migration metadata, data lineage, and audit trails

### Complete Example Properties File

See `transaction-test-audit.properties` for a complete example with audit fields enabled:

```properties
# ... connection settings ...

# Constant Columns Feature - Audit Fields
spark.cdm.feature.constantColumns.names=z_audit_crtd_by_txt,z_audit_evnt_id,z_audit_crtd_ts,z_audit_last_mdfd_by_txt
spark.cdm.feature.constantColumns.values='CDM_MIGRATION','MIGRATION_BATCH_001','2024-12-17T10:00:00Z','CDM_MIGRATION'
```

### Value Format Guidelines

- **Text/String values**: Use single quotes: `'CDM_MIGRATION'`
- **Numeric values**: No quotes: `12345`, `1702732800000`
- **Boolean values**: No quotes: `true`, `false`
- **Timestamp values**: ISO 8601 format with quotes: `'2024-12-17T10:00:00Z'` or `'2024-12-17T10:00:00.000Z'`
  - Supports both with and without milliseconds
  - The 'Z' suffix (UTC) is optional
- **Date values**: Date format with quotes: `'2024-12-17'`

**Supported Data Types:**
- String/Text (TEXT, VARCHAR)
- Integer (INT, INTEGER)
- Long (BIGINT)
- Double/Float (DOUBLE PRECISION, FLOAT, REAL)
- Boolean (BOOLEAN)
- Timestamp (TIMESTAMP, TIMESTAMP WITHOUT TIME ZONE)
- Date (DATE)

**Custom Delimiter (for values containing commas):**
If your constant values contain commas (e.g., in lists or complex strings), use a custom delimiter:
```properties
spark.cdm.feature.constantColumns.splitRegex=\\|
spark.cdm.feature.constantColumns.values='value1'|'value2,with,commas'|'value3'
```

### Verifying Audit Fields

After migration, verify the audit fields were populated:

```bash
# Check a few sample records
docker exec -i yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore -c "SELECT cmpny_id, accnt_nbr, z_audit_crtd_by_txt, z_audit_evnt_id, z_audit_crtd_ts FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr LIMIT 5;"'

# Verify all records have audit fields populated
docker exec -i yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore -c "SELECT COUNT(*) as total, COUNT(DISTINCT z_audit_crtd_by_txt) as distinct_created_by, COUNT(DISTINCT z_audit_evnt_id) as distinct_event_id FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr WHERE z_audit_crtd_by_txt = '\''CDM_MIGRATION'\'' AND z_audit_evnt_id = '\''MIGRATION_BATCH_001'\'';"'
```

**Expected Result:**
- All migrated records should have the audit fields populated
- `distinct_created_by` and `distinct_event_id` should be `1` (all records have the same constant values)
- `total` should match the number of migrated records

### Common Use Cases

| Use Case | Example Configuration |
|----------|----------------------|
| **Migration Tracking** | `created_by='CDM_MIGRATION'`, `migration_batch='BATCH_001'` |
| **Data Lineage** | `source_system='CASSANDRA_PROD'`, `migration_date='2024-12-17'` |
| **Version Control** | `data_version=1`, `schema_version='v2.0'` |
| **Audit Trail** | `migrated_at='2024-12-17T10:00:00Z'`, `migrated_by='system'` |

### Troubleshooting Audit Fields

**Issue: "Constant column X is not found on the target table"**
- Verify the column exists in the target YugabyteDB table
- Check for typos in column names
- Ensure column names match exactly (case-sensitive)

**Issue: "Constant column names and values are of different sizes"**
- Count items in `.names` and `.values` - they must match
- Check the delimiter is correctly splitting values

**Issue: "Constant column value cannot be parsed as type X"**
- Use correct CQLSH syntax for the data type
- Check quotes for string types
- Verify timestamp/date formats
- For timestamps, use ISO 8601 format: `'2024-12-17T10:00:00Z'` or `'2024-12-17T10:00:00.000Z'`

**Issue: "column X is of type timestamp but expression is of type character varying"**
- This was a bug that has been fixed. Ensure you're using the latest JAR build
- Timestamp values are now properly parsed and converted to Timestamp objects
- Rebuild JAR if needed: `mvn clean package -DskipTests`

**Issue: Values with commas not splitting correctly**
- Use `spark.cdm.feature.constantColumns.splitRegex` with a custom delimiter (e.g., `\\|`)
- Ensure the delimiter doesn't appear in your actual values

**Testing Status:**
- ✅ Tested with 100,000+ records
- ✅ All data types verified (String, Integer, Long, Double, Boolean, Timestamp, Date)
- ✅ Timestamp parsing verified (ISO 8601 format)
- ✅ All records correctly populated with constant values

For more details, see the [Audit Fields Guide](./mdfiles/AUDIT_FIELDS_GUIDE.md).

## Step 4: Monitor Progress

### Check Log File
```bash
tail -f migration.log
```

### Check Record Counts
```bash
# Cassandra (source)
docker exec -i cassandra cqlsh localhost 9042 -e "SELECT COUNT(*) FROM transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr;"

# YugabyteDB (target)
docker exec -i yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore -c "SELECT COUNT(*) FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr;"'
```

### Check Final Results
```bash
tail -50 migration.log | grep -E "(JobCounter|Final Read|Final Write|Final Error)"
```

## Step 5: Verify Migration

### Check for Errors
```bash
grep -i "ERROR" migration.log | tail -20
```

### Verify Data Integrity
```bash
# Compare record counts
# Source count should match target count
```

## Command Breakdown

| Parameter | Description |
|-----------|-------------|
| `--properties-file transaction-test.properties` | Loads all configuration from properties file |
| `--conf spark.cdm.schema.origin.keyspaceTable="..."` | Source table (Cassandra keyspace.table) |
| `--master "local[*]"` | Use all local CPU cores for parallel processing |
| `--driver-memory 4G` | Memory for Spark driver (increase for large datasets) |
| `--executor-memory 4G` | Memory for Spark executors (increase for large datasets) |
| `--class com.datastax.cdm.job.YugabyteMigrate` | Main class to run |
| `target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar` | JAR file location |

## For Different Tables

Simply replace the `keyspaceTable` value:

```bash
--conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.your_table"
```

## Performance Tuning

### For Small Datasets (< 10k records)
```bash
--driver-memory 2G \
--executor-memory 2G
```

### For Medium Datasets (10k - 100k records)
```bash
--driver-memory 4G \
--executor-memory 4G
```

### For Large Datasets (> 100k records)
```bash
--driver-memory 8G \
--executor-memory 8G
```

## Troubleshooting

### Issue: "No suitable driver"
- **Solution:** Ensure JAR is rebuilt: `mvn clean package -DskipTests`

### Issue: "Connection refused"
- **Solution:** Verify Cassandra and YugabyteDB are running:
  ```bash
  docker ps | grep -E "(cassandra|yugabyte)"
  ```

### Issue: "Too many clients"
- **Solution:** Reduce partitions in `transaction-test.properties`:
  ```properties
  spark.cdm.perfops.numParts=10  # Reduce from 20
  ```

### Issue: "ClassNotFoundException"
- **Solution:** Rebuild JAR: `mvn clean package -DskipTests`

## Quick Reference

### One-Line Commands

**Foreground:**
```bash
SPARK_HOME=$(brew --prefix apache-spark) && $SPARK_HOME/bin/spark-submit --properties-file transaction-test.properties --conf spark.cdm.schema.origin.keyspaceTable="transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr" --master "local[*]" --driver-memory 4G --executor-memory 4G --class com.datastax.cdm.job.YugabyteMigrate target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

**Background:**
```bash
SPARK_HOME=$(brew --prefix apache-spark) && nohup $SPARK_HOME/bin/spark-submit --properties-file transaction-test.properties --conf spark.cdm.schema.origin.keyspaceTable="transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr" --master "local[*]" --driver-memory 4G --executor-memory 4G --class com.datastax.cdm.job.YugabyteMigrate target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar > migration.log 2>&1 &
```

## Expected Output

When migration completes successfully, you should see:

```
INFO JobCounter: Final Read Record Count: 100000
INFO JobCounter: Final Write Record Count: 100000
INFO JobCounter: Final Skipped Record Count: 0
INFO JobCounter: Final Error Record Count: 0
INFO JobCounter: Final Partitions Passed: 20
INFO JobCounter: Final Partitions Failed: 0
```

## Performance Expectations

With Phase 1+2 optimizations enabled:
- **Throughput:** ~900-1000 records/second
- **IOPS:** ~20,000-25,000 (with batching)
- **Success Rate:** 100% (zero errors expected)

## Next Steps

After successful migration:
1. Verify data in YugabyteDB
2. Check performance logs in `migration_logs/` directory
3. Review failed records (if any) in `migration_logs/failed_records_*.csv`

