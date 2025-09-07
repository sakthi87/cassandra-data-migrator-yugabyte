# 🚀 Migration Troubleshooting Guide

## ✅ **Issues Fixed**

### **1. "Too Many Clients Already" Error**
- **Problem**: YugabyteDB connection limit exceeded
- **Solution**: Added connection retry logic with exponential backoff
- **Result**: Migration will retry failed connections up to 5 times

### **2. Multiple Log Files with Timestamps**
- **Problem**: Each Spark task created separate log files
- **Solution**: Implemented shared timestamp across all instances
- **Result**: All tasks now write to the same log files

## 🔧 **How to Run the Fixed Migration**

### **Step 1: Update Your Properties File**
```bash
# Copy the optimized properties file
cp optimized-migration.properties my-migration.properties

# Edit with your actual connection details
nano my-migration.properties
```

### **Step 2: Run the Migration**
```bash
spark-submit \
  --properties-file my-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="customer_datastore.customer_mtrc_by_lpid" \
  --master "local[*]" \
  --driver-memory 4G \
  --executor-memory 4G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## 📊 **Expected Results**

### **Connection Handling**
- ✅ Automatic retry on connection failures
- ✅ Exponential backoff (2s, 4s, 8s, 16s, 32s)
- ✅ Connection pooling to limit concurrent connections
- ✅ Proper connection cleanup

### **Log Files**
- ✅ Single set of log files per migration run
- ✅ `failed_records_YYYYMMDD_HHMMSS.csv` - Clean data for reprocessing
- ✅ `failed_keys_YYYYMMDD_HHMMSS.csv` - Keys and error reasons
- ✅ `performance_YYYYMMDD_HHMMSS.txt` - Performance metrics

## 🔍 **Monitoring Your Migration**

### **Check Connection Status**
```bash
# Monitor connection attempts in logs
grep "Connecting to YugabyteDB" migration_logs/*.log

# Check for retry attempts
grep "too many clients" migration_logs/*.log
```

### **Monitor Progress**
```bash
# Check performance metrics
tail -f migration_logs/performance_*.txt

# Monitor failed records
wc -l migration_logs/failed_records_*.csv
wc -l migration_logs/failed_keys_*.csv
```

## 🚨 **If You Still Get Connection Errors**

### **Option 1: Reduce Parallelism**
```properties
# In your properties file
spark.executor.cores=1
spark.executor.instances=3
spark.cdm.rate.target.writesPerSecond=200
```

### **Option 2: Increase YugabyteDB Connection Limit**
```sql
-- Connect to YugabyteDB and run:
ALTER SYSTEM SET max_connections = 200;
```

### **Option 3: Use Connection Pooling**
```properties
# Add to your properties file
spark.cdm.connect.target.yugabyte.maxConnections=5
spark.cdm.connect.target.yugabyte.connectionTimeout=60000
```

## 📈 **Performance Optimization**

### **For Large Datasets (96k+ records)**
```properties
# Optimized settings
spark.cdm.batch.size=500
spark.cdm.fetch.size=2000
spark.cdm.rate.target.writesPerSecond=1000
spark.executor.memory=8G
spark.driver.memory=4G
```

### **For Small Datasets (<10k records)**
```properties
# Conservative settings
spark.cdm.batch.size=100
spark.cdm.fetch.size=500
spark.cdm.rate.target.writesPerSecond=200
spark.executor.memory=2G
spark.driver.memory=2G
```

## 🔄 **Recovery Process**

### **If Migration Fails Partially**
1. **Check failed records**:
   ```bash
   cat migration_logs/failed_records_*.csv
   ```

2. **Reprocess failed records**:
   ```bash
   # Use the failed_records file as input for reprocessing
   # (This would require a custom script)
   ```

3. **Validate data integrity**:
   ```bash
   # Run validation job
   spark-submit \
     --properties-file my-migration.properties \
     --class com.datastax.cdm.job.YugabyteValidate \
     target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
   ```

## 📋 **File Locations**

### **Log Files**
- **Location**: `./migration_logs/` (or custom directory via `spark.cdm.log.directory`)
- **Files**:
  - `failed_records_YYYYMMDD_HHMMSS.csv`
  - `failed_keys_YYYYMMDD_HHMMSS.csv`
  - `performance_YYYYMMDD_HHMMSS.txt`

### **CDM Logs**
- **Location**: `./cdm_logs/`
- **Files**:
  - `cdm.log`
  - `cdm_errors.log`
  - `cdm_diff.log`

## 🎯 **Success Indicators**

### **Migration Complete**
- ✅ No "too many clients" errors in logs
- ✅ All 96k records processed
- ✅ Performance metrics show completion
- ✅ Failed records file is empty or minimal

### **Data Validation**
- ✅ Source and target record counts match
- ✅ No data type conversion errors
- ✅ All primary keys preserved

## 🆘 **Need Help?**

If you encounter issues:
1. Check the log files in `migration_logs/`
2. Look for specific error messages
3. Adjust connection limits in properties file
4. Reduce parallelism if needed

The fixes should resolve both the connection limit issue and the multiple log files problem! 🎉
