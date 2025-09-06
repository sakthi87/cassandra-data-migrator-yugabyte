# Multi-Node Cluster Analysis for YugabyteDB CDM

## 🏗️ **Why Default Token Range is Better Than Node-by-Node**

### **❌ Problems with Node-by-Node Approach:**

1. **Complexity**: You'd need to:
   - Query each node individually
   - Manage multiple connections
   - Handle node failures manually
   - Coordinate token ranges across nodes

2. **Performance Issues**:
   - Multiple connection overhead
   - Potential for uneven load distribution
   - Complex error handling

3. **Maintenance**:
   - Need to update when nodes are added/removed
   - Harder to monitor and debug

### **✅ Benefits of Default Token Range:**

1. **Automatic Distribution**:
   - CDM connects to ANY node
   - Cassandra driver handles node discovery
   - Reads distributed automatically across all nodes

2. **Built-in Fault Tolerance**:
   - If a node fails, reads continue from other nodes
   - Automatic retry logic
   - Load balancing handled by driver

3. **Simplified Management**:
   - Single connection point
   - Automatic token range distribution
   - Built-in monitoring and error handling

## 🔍 **How CDM Handles Multi-Node Reads**

### **1. Connection Strategy:**
```java
// CDM connects to any node in your cluster
originConnection = connectionFetcher.getConnection(sContext.getConf, Side.ORIGIN, consistencyLevel, runId)
```

### **2. Token Range Distribution:**
```java
// CDM splits the full token range into partitions
// Each partition is processed by Spark workers
// Cassandra driver routes each query to the appropriate node(s)
SplitPartitions.getRandomSubPartitions(pieces, minPartition, maxPartition, coveragePercent, jobType)
```

### **3. Query Execution:**
```sql
-- CDM generates queries like this for each partition
SELECT * FROM customer 
WHERE TOKEN(customer_id) >= -9223372036854775808 
  AND TOKEN(customer_id) <= 9223372036854775807
ALLOW FILTERING
```

**Cassandra automatically:**
- Routes the query to nodes responsible for that token range
- Handles replication and consistency
- Returns only the data for that range

## 🎯 **Recommended Approach for Your 13M Row Customer Table**

### **Option 1: Full Cluster Migration (Recommended)**
```bash
# This automatically handles all nodes in your cluster
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.trackRun=true \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

### **Option 2: Custom Token Range (If Needed)**
```bash
# Only if you want to migrate specific data ranges
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.filter.cassandra.partition.min="<CUSTOM_MIN>" \
  --conf spark.cdm.filter.cassandra.partition.max="<CUSTOM_MAX>" \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## 📊 **Performance Comparison**

| Approach | Complexity | Performance | Fault Tolerance | Maintenance |
|----------|------------|-------------|-----------------|-------------|
| **Default Token Range** | ✅ Simple | ✅ Optimal | ✅ Built-in | ✅ Minimal |
| **Node-by-Node** | ❌ Complex | ❌ Suboptimal | ❌ Manual | ❌ High |

## 🚀 **Best Practices**

1. **Use Default Token Range**: Let CDM handle multi-node distribution
2. **Monitor Progress**: Use tracking tables to monitor migration
3. **Handle Failures**: Use incremental updates for failed partitions
4. **Optimize Spark**: Tune memory and parallelism settings
5. **Validate Results**: Compare row counts and data integrity

## 🔧 **Configuration for Multi-Node Clusters**

### **Connection Settings:**
```properties
# Connect to any node in your cluster
spark.cdm.connect.origin.host=node1.your-cluster.com
spark.cdm.connect.origin.port=9042

# Or use multiple hosts for better fault tolerance
spark.cdm.connect.origin.host=node1.your-cluster.com,node2.your-cluster.com,node3.your-cluster.com
```

### **Performance Tuning:**
```properties
# Increase parallelism for multi-node clusters
spark.cdm.perf.numParts=64
spark.cdm.tokenCoveragePercent=100

# Optimize for large datasets
spark.cdm.read.cl=LOCAL_QUORUM
spark.cdm.origin.fetchSizeInRows=5000
```

## 📈 **Expected Performance**

For your 13M row customer table:
- **Single Node**: ~2-4 hours
- **3-Node Cluster**: ~1-2 hours (with proper parallelism)
- **5+ Node Cluster**: ~30-60 minutes (with optimal configuration)

The CDM automatically scales with your cluster size!
