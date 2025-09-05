# Running YugabyteDB Migration with Spark Submit

## 🚀 **Correct Command (Following Original CDM Pattern)**

The original CDM uses `spark-submit` because it's a Spark application. Our YugabyteDB version should follow the same pattern:

### **Basic Command:**
```bash
spark-submit --properties-file yugabyte-migrate.properties \
--conf spark.cdm.schema.origin.keyspaceTable="<keyspacename>.<tablename>" \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.YugabyteMigrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> logfile_name_$(date +%Y%m%d_%H_%M).txt
```

### **Example for Your Customer Table:**
```bash
spark-submit --properties-file yugabyte-migrate.properties \
--conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.YugabyteMigrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

## 🔧 **Why We Need Explicit Target Specification**

### **Original CDM (Cassandra-to-Cassandra):**
- **Same protocol**: Both use CQL/Cassandra drivers
- **Automatic target detection**: CDM can auto-discover target schema
- **Unified configuration**: Single set of connection properties

### **Our YugabyteDB Version (Cassandra-to-PostgreSQL):**
- **Different protocols**: CQL for origin, PostgreSQL for target
- **Explicit target specification**: We need to specify YugabyteDB connection details
- **Custom data type mapping**: Cassandra types → PostgreSQL types

## 📋 **Configuration Differences**

### **Original CDM Properties:**
```properties
# Origin (Cassandra)
spark.cdm.connect.origin.host=localhost
spark.cdm.connect.origin.port=9042

# Target (Cassandra) - Same protocol
spark.cdm.connect.target.host=localhost
spark.cdm.connect.target.port=9042
```

### **Our YugabyteDB Properties:**
```properties
# Origin (Cassandra)
spark.cdm.connect.origin.host=your-cassandra-host
spark.cdm.connect.origin.port=9042

# Target (YugabyteDB YSQL) - Different protocol
spark.cdm.connect.target.type=yugabyte
spark.cdm.connect.target.yugabyte.host=your-yugabyte-host
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=yugabyte
```

## 🎯 **Complete Example for Your 13M Row Migration**

### **1. Update Configuration:**
```bash
# Edit your configuration file
nano yugabyte-migrate.properties
```

### **2. Run Migration:**
```bash
spark-submit --properties-file yugabyte-migrate.properties \
--conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
--conf spark.cdm.trackRun=true \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.YugabyteMigrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

### **3. Monitor Progress:**
```bash
# Check the log file
tail -f customer_migration_*.txt

# Check Spark UI (if running locally)
# Open http://localhost:4040 in your browser
```

## 🔍 **Key Differences from Original CDM**

| Aspect | Original CDM | Our YugabyteDB Version |
|--------|-------------|----------------------|
| **Command** | `spark-submit` | `spark-submit` ✅ |
| **Main Class** | `com.datastax.cdm.job.Migrate` | `com.datastax.cdm.job.YugabyteMigrate` |
| **Target Protocol** | CQL (Cassandra) | PostgreSQL (YugabyteDB) |
| **Data Types** | Cassandra → Cassandra | Cassandra → PostgreSQL |
| **Connection** | Auto-detected | Explicit YugabyteDB config |

## ⚡ **Performance Tuning for 13M Rows**

```bash
spark-submit --properties-file yugabyte-migrate.properties \
--conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
--conf spark.cdm.trackRun=true \
--conf spark.cdm.perfops.numParts=1000 \
--conf spark.cdm.perfops.batchSize=1 \
--conf spark.cdm.perfops.fetchSizeInRows=500 \
--conf spark.cdm.perfops.ratelimit.origin=10000 \
--conf spark.cdm.perfops.ratelimit.target=10000 \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.YugabyteMigrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

## 🎉 **Summary**

You were absolutely correct! The original CDM uses `spark-submit`, and our YugabyteDB version should follow the same pattern. The key differences are:

1. **Same command structure**: `spark-submit` with properties file
2. **Different main class**: `YugabyteMigrate` instead of `Migrate`
3. **Explicit target configuration**: YugabyteDB connection details instead of auto-detection
4. **Custom data type mapping**: Cassandra → PostgreSQL conversion

This maintains consistency with the original CDM while adding YugabyteDB support! 🚀
