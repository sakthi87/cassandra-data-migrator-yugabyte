# Final Migration Command for Customer Table

## 🎯 **The Correct Command to Use**

Based on the CDM GitRepo pattern and our YugabyteDB modifications, here's the **final, production-ready command**:

```bash
spark-submit --properties-file yugabyte-migrate.properties \
--conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
--conf spark.cdm.trackRun=true \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.YugabyteMigrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

## 🔧 **Why This Command is Correct**

### **✅ Follows CDM GitRepo Pattern:**
- Uses `spark-submit` (not `java -cp`)
- Includes `--properties-file` for configuration
- Specifies `--conf spark.cdm.schema.origin.keyspaceTable`
- Includes Spark configuration (`--master`, `--driver-memory`, `--executor-memory`)
- Redirects output to log file

### **✅ Includes Our YugabyteDB Modifications:**
- Uses `--class com.datastax.cdm.job.YugabyteMigrate` (our custom class)
- PostgreSQL driver is already included in the shaded JAR
- No need for `--packages` parameter

### **✅ Production-Ready Features:**
- Run tracking enabled (`--conf spark.cdm.trackRun=true`)
- Proper memory allocation for 13M rows
- Log file generation for monitoring

## 📝 **Step-by-Step Execution**

### **1. Update Your Configuration:**
```bash
# Edit your configuration file
nano yugabyte-migrate.properties
```

Make sure it includes:
```properties
# Origin (DataStax Cassandra)
spark.cdm.connect.origin.host=your-cassandra-host
spark.cdm.connect.origin.port=9042
spark.cdm.connect.origin.username=cassandra
spark.cdm.connect.origin.password=cassandra

# Target (YugabyteDB YSQL)
spark.cdm.connect.target.type=yugabyte
spark.cdm.connect.target.yugabyte.host=your-yugabyte-host
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=yugabyte
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=yugabyte

# Table specification
spark.cdm.schema.origin.keyspaceTable=your_keyspace.customer
spark.cdm.schema.target.keyspaceTable=your_keyspace.customer
```

### **2. Create Target Table in YugabyteDB:**
```sql
-- Connect to YugabyteDB YSQL
psql -h your-yugabyte-host -p 5433 -U yugabyte -d yugabyte

-- Create the target table (adjust schema as needed)
CREATE TABLE your_keyspace.customer (
    customer_id TEXT PRIMARY KEY,
    -- Add other columns based on your Cassandra schema
    -- CDM will automatically map Cassandra types to PostgreSQL types
);
```

### **3. Run the Migration:**
```bash
spark-submit --properties-file yugabyte-migrate.properties \
--conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
--conf spark.cdm.trackRun=true \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.YugabyteMigrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

### **4. Monitor Progress:**
```bash
# Check the log file
tail -f customer_migration_*.txt

# Check Spark UI (if running locally)
# Open http://localhost:4040 in your browser
```

## ⚡ **Performance Tuning for 13M Rows**

If you need better performance, add these additional configurations:

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

**Use the second command** (based on CDM GitRepo) because it:

1. **Follows the official CDM pattern** from the GitHub repository
2. **Includes all necessary Spark configuration** for large-scale migration
3. **Has proper logging and monitoring** capabilities
4. **Is production-ready** for your 13M row customer table migration

The key difference is that we're using our custom `YugabyteMigrate` class instead of the original `Migrate` class, but following the exact same command structure and patterns as the official CDM! 🚀
