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

# Table Configuration
spark.cdm.schema.origin.keyspaceTable=transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr
spark.cdm.schema.target.keyspaceTable=transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr
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

