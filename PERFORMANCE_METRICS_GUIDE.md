# Performance Metrics Guide

This guide explains where to find performance metrics, timing information, and throughput statistics in CDM migration logs.

## 📊 Where to Find Metrics

### 1. Performance Summary File (Primary Source)

**Location:** `migration_logs/migration_summary_YYYYMMDD_HHMMSS.txt`

**Example filename:** `migration_logs/migration_summary_20251218_001234.txt`

This file contains:
- ✅ Real-time progress updates during migration
- ✅ Final comprehensive summary with all metrics
- ✅ Timing information
- ✅ Throughput statistics (TPS/records per second)
- ✅ Error rates and success rates

**How to find it:**
```bash
# List all summary files
ls -lht migration_logs/migration_summary_*.txt

# View the latest summary
ls -t migration_logs/migration_summary_*.txt | head -1 | xargs cat

# Or watch it in real-time during migration
tail -f migration_logs/migration_summary_*.txt
```

---

### 2. Spark Application Logs

**Location:** Standard Spark output (console or log files)

**What to look for:**
- Partition completion messages
- Batch statistics
- Final record counts

**Example log entries:**
```
INFO YugabyteCopyJobSession: Partition complete. Total batches: 500, Total records written: 10000
INFO JobCounter: Final Write Record Count: 100000
INFO JobCounter: Final Read Record Count: 100000
```

---

### 3. Real-time Progress Updates

During migration, the performance summary file is updated in real-time with progress information:

```
[12:34:56] Progress: Reads: 50000, Writes: 50000, Errors: 0, Skipped: 0, 
  Partitions: 10/10, Throughput: 15234.56 records/sec
```

---

## 📈 Metrics Explained

### Timing Information

**Total Migration Duration:**
- Format: `X hours Y minutes Z seconds` or `X minutes Y seconds`
- Shows: Total time from start to completion
- Example: `Total Migration Duration: 5 minutes 23 seconds`

**Start/End Times:**
- Format: `yyyy-MM-dd HH:mm:ss`
- Example: 
  ```
  Migration Start Time: 2025-12-18 00:12:34
  Migration End Time: 2025-12-18 00:17:57
  ```

---

### Record Statistics

**Total Records Read:**
- Number of records read from source (Cassandra)
- Should match source table count

**Total Records Written:**
- Number of records successfully written to target (YugabyteDB)
- Should match `Total Records Read` (if no errors)

**Total Records Failed:**
- Number of records that failed to migrate
- Check `failed_records_*.csv` for details

**Total Records Skipped:**
- Records skipped due to filtering or validation
- Usually 0 unless filters are configured

---

### Performance Metrics (TPS/Throughput)

**Average Throughput:**
- **Unit:** `records/sec` (also called TPS - Transactions Per Second)
- **Calculation:** `(Total Records Written × 1000) / Total Time (milliseconds)`
- **Example:** `Average Throughput: 15234.56 records/sec`
- **Meaning:** Average number of records processed per second over the entire migration

**Peak Throughput:**
- **Unit:** `records/sec`
- **Calculation:** Estimated peak rate (assumes 80% active time)
- **Example:** `Peak Throughput: 19043.20 records/sec`
- **Meaning:** Highest sustained throughput during migration

**Current Throughput (Real-time):**
- **Unit:** `records/sec`
- **Shown in:** Real-time progress updates
- **Example:** `Throughput: 15234.56 records/sec`
- **Meaning:** Current rate at the time of the update

---

### Partition Statistics

**Total Partitions Processed:**
- Number of Spark partitions successfully processed
- Related to `spark.cdm.perfops.numParts` configuration

**Total Partitions Failed:**
- Number of partitions that failed
- Should be 0 for successful migrations

**Partition Success Rate:**
- Percentage of partitions processed successfully
- Example: `Partition Success Rate: 100.00%`

---

### Data Quality Metrics

**Error Rate:**
- **Unit:** `%`
- **Calculation:** `(Total Errors / Total Reads) × 100`
- **Example:** `Error Rate: 0.00%`
- **Target:** Should be 0% for successful migration

**Success Rate:**
- **Unit:** `%`
- **Calculation:** `100% - Error Rate`
- **Example:** `Success Rate: 100.00%`
- **Target:** Should be 100% for successful migration

---

## 🔍 How to Extract Metrics

### Quick Metrics Extraction

```bash
# Extract total time
grep "Total Migration Duration" migration_logs/migration_summary_*.txt

# Extract throughput
grep "Average Throughput" migration_logs/migration_summary_*.txt

# Extract record counts
grep -E "Total Records (Read|Written|Failed)" migration_logs/migration_summary_*.txt

# Extract all performance metrics
grep -A 10 "=== PERFORMANCE METRICS ===" migration_logs/migration_summary_*.txt
```

### Complete Summary Extraction

```bash
# View entire final summary
grep -A 100 "=== FINAL MIGRATION SUMMARY ===" migration_logs/migration_summary_*.txt | tail -50
```

### Real-time Monitoring Script

```bash
#!/bin/bash
# monitor_migration.sh

SUMMARY_FILE=$(ls -t migration_logs/migration_summary_*.txt 2>/dev/null | head -1)

if [ -z "$SUMMARY_FILE" ]; then
    echo "No migration summary file found"
    exit 1
fi

echo "Monitoring: $SUMMARY_FILE"
echo "Press Ctrl+C to stop"
echo ""

tail -f "$SUMMARY_FILE" | while read line; do
    if [[ $line == *"Throughput:"* ]]; then
        echo "$(date '+%H:%M:%S') - $line"
    elif [[ $line == *"Progress:"* ]]; then
        echo "$(date '+%H:%M:%S') - $line"
    fi
done
```

---

## 📋 Example Summary Output

Here's what a complete final summary looks like:

```
==========================================
=== FINAL MIGRATION SUMMARY ===
==========================================

=== TIMING INFORMATION ===
Migration Start Time: 2025-12-18 00:12:34
Migration End Time: 2025-12-18 00:17:57
Total Migration Duration: 5 minutes 23 seconds

=== RECORD STATISTICS ===
Total Records Read: 100000
Total Records Written: 100000
Total Records Failed: 0
Total Records Skipped: 0

=== PARTITION STATISTICS ===
Total Partitions Processed: 20
Total Partitions Failed: 0
Partition Success Rate: 100.00%

=== PERFORMANCE METRICS ===
Average Throughput: 15234.56 records/sec
Peak Throughput: 19043.20 records/sec
Error Rate: 0.00%
Success Rate: 100.00%

=== DATA QUALITY ===
✅ MIGRATION COMPLETED SUCCESSFULLY
✅ All records migrated without errors

=== RECOMMENDATIONS ===
✅ Excellent performance! Migration completed successfully.

==========================================
Migration Summary Complete
==========================================
```

---

## 🧮 Calculating Additional Metrics

### Records Per Hour
```bash
# From summary file
THROUGHPUT=$(grep "Average Throughput" migration_logs/migration_summary_*.txt | grep -oP '\d+\.\d+' | head -1)
RECORDS_PER_HOUR=$(echo "$THROUGHPUT * 3600" | bc)
echo "Records per hour: $RECORDS_PER_HOUR"
```

### Estimated Time for Large Migrations
```bash
# Example: Estimate time for 6.4 million records
TOTAL_RECORDS=6400000
THROUGHPUT=15234.56  # records/sec from your summary
TIME_SECONDS=$(echo "scale=2; $TOTAL_RECORDS / $THROUGHPUT" | bc)
TIME_HOURS=$(echo "scale=2; $TIME_SECONDS / 3600" | bc)
echo "Estimated time: $TIME_HOURS hours ($TIME_SECONDS seconds)"
```

### IOPS Calculation
For YugabyteDB, throughput in records/sec is equivalent to IOPS (Input/Output Operations Per Second) when each record is one write operation.

**Example:**
- `Average Throughput: 15234.56 records/sec` = **15,234.56 IOPS**

---

## ⚠️ Troubleshooting

### Issue: No summary file found

**Possible causes:**
1. Migration hasn't started yet
2. Log directory not created
3. Migration failed before initialization

**Solution:**
```bash
# Check if migration is running
ps aux | grep spark-submit

# Check log directory
ls -la migration_logs/

# Check Spark logs for errors
grep -i error spark-*.log
```

### Issue: Metrics show 0 records

**Possible causes:**
1. Migration just started
2. No data in source table
3. All records filtered out

**Solution:**
- Wait for migration to progress
- Check source table has data
- Review filter configurations

### Issue: Throughput seems low

**Check:**
1. Batch size configuration: `spark.cdm.connect.target.yugabyte.batchSize`
2. Number of partitions: `spark.cdm.perfops.numParts`
3. Connection pool size: `spark.cdm.connect.target.yugabyte.pool.maxSize`
4. Network latency between Spark and YugabyteDB
5. YugabyteDB resource constraints

---

## 📝 Key Metrics to Monitor

### During Migration (Real-time)
- ✅ **Current Throughput** - Should be stable and high
- ✅ **Progress** - Reads/Writes should be increasing
- ✅ **Errors** - Should remain at 0

### After Migration (Final Summary)
- ✅ **Total Migration Duration** - Overall time taken
- ✅ **Average Throughput** - Performance indicator
- ✅ **Total Records Written** - Should match source count
- ✅ **Error Rate** - Should be 0%
- ✅ **Success Rate** - Should be 100%

---

## 🎯 Performance Targets

Based on our optimizations:

- **Target Throughput:** 15,000 - 20,000 records/sec (15K-20K IOPS)
- **Minimum Acceptable:** 10,000 records/sec (10K IOPS)
- **Excellent Performance:** > 20,000 records/sec (20K+ IOPS)

**For 6.4 million records:**
- At 15K records/sec: ~7 minutes
- At 20K records/sec: ~5.3 minutes
- At 10K records/sec: ~10.7 minutes

---

## 📚 Related Files

- `migration_logs/migration_summary_*.txt` - Performance summary
- `migration_logs/failed_records_*.csv` - Failed records (if any)
- `migration_logs/failed_keys_*.csv` - Failed keys (if any)
- Spark application logs - Detailed execution logs

---

## 💡 Tips

1. **Monitor in real-time:** Use `tail -f` to watch progress during migration
2. **Save summaries:** Keep summary files for performance tracking
3. **Compare runs:** Track throughput across different configurations
4. **Check regularly:** Monitor for errors during long migrations
5. **Use scripts:** Automate metric extraction for reporting

