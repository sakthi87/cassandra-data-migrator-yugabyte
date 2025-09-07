# YugabyteDB Migration Reconciliation Guide

This guide explains how to validate and reconcile your Cassandra-to-YugabyteDB migration to ensure all records match between source and target.

## 🔍 **CDM Validation Support**

CDM provides comprehensive validation capabilities through different job types:

### **Available Job Types**
1. **MIGRATE** - Copy data from source to target
2. **VALIDATE** - Compare source and target data
3. **GUARDRAIL** - Check data quality and constraints

## 🚀 **How to Run Validation**

### **Step 1: Run Migration First**
```bash
spark-submit \
  --properties-file enhanced-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.your_table" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

### **Step 2: Run Validation**
```bash
spark-submit \
  --properties-file validation-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.your_table" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteValidate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## 📊 **Validation Results**

The validation process will generate detailed reports:

### **Console Output**
```
Final Valid Record Count: 1,000,000
Final Mismatch Record Count: 0
Final Missing Record Count: 0
Final Error Record Count: 0
Final Partitions Passed: 100
Final Partitions Failed: 0
```

### **Log Files Generated**
- `validation_logs/validation_YYYYMMDD_HHMMSS.txt` - Performance metrics
- `validation_logs/failed_records_YYYYMMDD_HHMMSS.csv` - Detailed mismatch data
- `validation_logs/failed_keys_YYYYMMDD_HHMMSS.csv` - Mismatch summary

## 🔧 **Validation Features**

### **What Gets Validated**
- ✅ **Record Count** - Same number of records in both systems
- ✅ **Primary Key Matching** - All primary keys exist in target
- ✅ **Data Integrity** - All column values match exactly
- ✅ **Data Types** - Proper type conversion validation

### **Validation Types**
1. **VALID** - Record matches perfectly
2. **MISMATCHED** - Record exists but data differs
3. **MISSING** - Record exists in source but not in target
4. **ERROR** - Validation failed due to technical issues

## 📈 **Performance Monitoring**

The validation process tracks:
- **Throughput** - Records validated per second
- **Duration** - Total validation time
- **Error Rate** - Percentage of validation failures
- **Partition Progress** - Real-time progress updates

## 🛠️ **Troubleshooting Validation Issues**

### **Common Issues and Solutions**

#### **Missing Records**
```
ERROR: Missing record in target for key: [user123]
```
**Solution**: Re-run migration for specific partitions or check for data filtering issues.

#### **Mismatched Data**
```
ERROR: Mismatch found for key: [user123] - Column email: Origin='user@example.com' Target='user@old.com'
```
**Solution**: Check data type conversions or transformation logic.

#### **Connection Issues**
```
ERROR: SQL error during validation
```
**Solution**: Verify YugabyteDB connection settings and network connectivity.

## 📋 **Validation Checklist**

### **Before Validation**
- [ ] Migration completed successfully
- [ ] No failed records in migration logs
- [ ] YugabyteDB is accessible and responsive
- [ ] Validation properties file configured correctly

### **After Validation**
- [ ] All records show as VALID
- [ ] No MISMATCHED or MISSING records
- [ ] Performance metrics within expected range
- [ ] Validation logs saved for audit

## 🔄 **Automated Validation Workflow**

### **Complete Migration + Validation Script**
```bash
#!/bin/bash

echo "Starting Cassandra to YugabyteDB Migration..."

# Step 1: Run Migration
echo "Step 1: Running Migration..."
spark-submit \
  --properties-file enhanced-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.your_table" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar

if [ $? -eq 0 ]; then
    echo "Migration completed successfully!"
    
    # Step 2: Run Validation
    echo "Step 2: Running Validation..."
    spark-submit \
      --properties-file validation-migration.properties \
      --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.your_table" \
      --master "local[*]" \
      --driver-memory 25G \
      --executor-memory 25G \
      --class com.datastax.cdm.job.YugabyteValidate \
      target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
    
    if [ $? -eq 0 ]; then
        echo "Validation completed successfully!"
        echo "Migration and validation completed successfully!"
    else
        echo "Validation failed! Check validation logs for details."
        exit 1
    fi
else
    echo "Migration failed! Check migration logs for details."
    exit 1
fi
```

## 📊 **Understanding Validation Metrics**

### **Key Metrics to Monitor**
- **Valid Record Count** - Should equal total records migrated
- **Mismatch Record Count** - Should be 0 for successful migration
- **Missing Record Count** - Should be 0 for complete migration
- **Error Record Count** - Should be 0 for clean migration
- **Throughput** - Higher is better (records/second)

### **Success Criteria**
- ✅ **100% Valid Records** - All records match perfectly
- ✅ **0 Mismatched Records** - No data differences
- ✅ **0 Missing Records** - All source records present in target
- ✅ **0 Error Records** - No technical validation failures

## 🚨 **What to Do If Validation Fails**

### **If Records Are Missing**
1. Check migration logs for failed records
2. Re-run migration for specific partitions
3. Verify data filtering logic

### **If Records Are Mismatched**
1. Review data type conversion logic
2. Check for timezone or encoding issues
3. Verify column mapping configuration

### **If Validation Errors Occur**
1. Check YugabyteDB connectivity
2. Verify database permissions
3. Review validation logs for specific error details

## 📞 **Support and Troubleshooting**

For additional help:
1. Check the generated log files in `validation_logs/`
2. Review the performance metrics for bottlenecks
3. Use the failed records CSV files for detailed analysis
4. Re-run validation on specific partitions if needed

---

**Note**: This validation process ensures 100% data integrity between your Cassandra source and YugabyteDB target, giving you confidence that your migration was successful.
