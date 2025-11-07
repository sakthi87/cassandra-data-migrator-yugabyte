# Cassandra Data Migrator (CDM) - Complete Parameters Reference

This document provides comprehensive documentation for all configuration parameters used in CDM for migrating data from Cassandra to YugabyteDB.

## Table of Contents

1. [Spark Configuration Parameters](#spark-configuration-parameters)
2. [CDM Performance Parameters](#cdm-performance-parameters)
3. [Connection Configuration Parameters](#connection-configuration-parameters)
4. [Throughput Reference Tables](#throughput-reference-tables)
5. [Memory Requirements Reference](#memory-requirements-reference)
6. [Quick Configuration Examples](#quick-configuration-examples)

---

## Spark Configuration Parameters

### Resource Allocation Parameters

#### `spark.executor.cores`
- **Description**: Number of CPU cores allocated to each executor (worker process)
- **Type**: Integer
- **Default**: 1
- **Recommended**: 2-8 cores per executor
- **Usage**: Controls parallelism within each executor. Higher values allow more parallel tasks per executor.
- **Example**: `spark.executor.cores=4` means each executor can run 4 tasks in parallel

#### `spark.executor.instances`
- **Description**: Number of executor instances (worker processes) to create
- **Type**: Integer
- **Default**: 1
- **Recommended**: 
  - Local mode: 1-4 instances
  - Cluster mode: Based on cluster size
- **Usage**: Total parallelism = instances × cores. More instances = more total parallel tasks.
- **Example**: `spark.executor.instances=4` with `spark.executor.cores=4` = 16 parallel tasks

#### `spark.executor.memory`
- **Description**: Heap memory allocated to each executor
- **Type**: Memory size (e.g., 6G, 8G, 12G)
- **Default**: 1G
- **Recommended**: 4-12G depending on data size
- **Usage**: Used for data caching, shuffles, and task execution. Leave 10-20% for overhead.
- **Example**: `spark.executor.memory=6G` allocates 6GB heap per executor

#### `spark.driver.memory`
- **Description**: Memory allocated to the Spark driver (coordinator/master)
- **Type**: Memory size (e.g., 6G, 8G, 12G)
- **Default**: 1G
- **Recommended**: Match or exceed executor memory for local mode
- **Usage**: Driver coordinates tasks, collects results, manages metadata. Needs more memory if collecting large results.
- **Example**: `spark.driver.memory=6G` allocates 6GB to the driver

### Adaptive Query Execution (AQE) Parameters

#### `spark.sql.adaptive.enabled`
- **Description**: Enable Adaptive Query Execution (Spark 3.0+)
- **Type**: Boolean
- **Default**: false
- **Recommended**: true (always enable)
- **Usage**: Dynamically optimizes query execution based on runtime statistics. Automatically adjusts join strategies, partition sizes, etc.
- **Example**: `spark.sql.adaptive.enabled=true`

#### `spark.sql.adaptive.coalescePartitions.enabled`
- **Description**: Enable adaptive partition coalescing
- **Type**: Boolean
- **Default**: false (if AQE enabled, default is true)
- **Recommended**: true
- **Usage**: Combines small partitions after shuffle operations to reduce overhead from too many small partitions.
- **Example**: `spark.sql.adaptive.coalescePartitions.enabled=true`

#### `spark.sql.adaptive.skewJoin.enabled`
- **Description**: Enable adaptive skew join handling
- **Type**: Boolean
- **Default**: false (if AQE enabled, default is true)
- **Recommended**: true
- **Usage**: Detects and handles data skew in joins automatically by splitting large partitions to balance workload.
- **Example**: `spark.sql.adaptive.skewJoin.enabled=true`

#### `spark.sql.adaptive.advisoryPartitionSizeInBytes`
- **Description**: Target partition size for adaptive execution
- **Type**: Memory size (e.g., 32MB, 64MB, 128MB)
- **Default**: 64MB
- **Recommended**: 32-128MB depending on data size
- **Usage**: AQE tries to create partitions of this size. Smaller = more parallelism but more overhead. Larger = less overhead but less parallelism.
- **Example**: `spark.sql.adaptive.advisoryPartitionSizeInBytes=32MB`

### Serialization Parameters

#### `spark.serializer`
- **Description**: Serializer class to use for data serialization
- **Type**: String (class name)
- **Default**: `org.apache.spark.serializer.JavaSerializer`
- **Recommended**: `org.apache.spark.serializer.KryoSerializer`
- **Usage**: Kryo is 2-10x faster and produces smaller serialized data, improving network transfer efficiency.
- **Example**: `spark.serializer=org.apache.spark.serializer.KryoSerializer`

### Timeout Parameters

#### `spark.network.timeout`
- **Description**: Network timeout for all network operations
- **Type**: Duration (e.g., 600s, 1200s)
- **Default**: 120s
- **Recommended**: 600s (10 minutes) for large migrations
- **Usage**: Time to wait for network I/O (fetching data, shuffles, etc.). Increase for slow networks or large data transfers.
- **Example**: `spark.network.timeout=600s`

#### `spark.sql.broadcastTimeout`
- **Description**: Timeout for broadcast join operations
- **Type**: Duration (e.g., 600s, 1200s)
- **Default**: 300s
- **Recommended**: 600s (should be >= network.timeout)
- **Usage**: Time to wait for broadcast tables to be distributed to executors. Increase if broadcasting large tables (>100MB).
- **Example**: `spark.sql.broadcastTimeout=600s`

---

## CDM Performance Parameters

### Partitioning Parameters

#### `spark.cdm.perfops.numParts`
- **Description**: Number of partitions to divide the token range into
- **Type**: Integer
- **Default**: 5000
- **Recommended**: table-size / 10MB (aim for ~10MB per partition)
- **Usage**: The full token range (-2^63 to 2^63-1) is divided into this many parts for parallel processing.
- **Calculation**: 
  - 4GB table = 400 partitions
  - 40GB table = 4000 partitions
  - 400GB table = 40000 partitions
- **Example**: `spark.cdm.perfops.numParts=400` for a ~4GB table

### Rate Limiting Parameters

#### `spark.cdm.perfops.ratelimit.origin`
- **Description**: Maximum read operations per second from Origin (Cassandra)
- **Type**: Integer
- **Default**: 20000
- **Recommended**: Based on origin cluster capacity (start conservative, increase gradually)
- **Usage**: Rate limiter prevents overwhelming the origin cluster. Applied per executor in cluster mode, globally in local mode.
- **Formula (Cluster Mode)**: Effective reads/sec = ratelimit.origin × executor.instances
- **Example**: `spark.cdm.perfops.ratelimit.origin=5000`

#### `spark.cdm.perfops.ratelimit.target`
- **Description**: Maximum write operations per second to Target (YugabyteDB)
- **Type**: Integer
- **Default**: 20000
- **Recommended**: Based on target cluster capacity (start conservative, increase gradually)
- **Usage**: Rate limiter prevents overwhelming the target cluster. Applied per executor in cluster mode, globally in local mode.
- **Formula (Cluster Mode)**: Effective writes/sec = ratelimit.target × executor.instances
- **Important**: Throughput = MIN(ratelimit.origin, ratelimit.target)
- **Example**: `spark.cdm.perfops.ratelimit.target=5000`

### Batching Parameters

#### `spark.cdm.perfops.batchSize`
- **Description**: Number of records to batch together when writing to Target
- **Type**: Integer
- **Default**: 5
- **Recommended**: Based on row size:
  - Small rows (< 1KB): 100-5000
  - Medium rows (1-10KB): 100-1000
  - Large rows (10-100KB): 10-100
  - Very large rows (> 100KB): 1-10
  - If primary-key = partition-key: Use 1
- **Usage**: Records are grouped into UNLOGGED batches for efficient writes. Larger batches = fewer network round-trips = better throughput.
- **Memory Impact**: batchSize × avg_row_size = memory per batch
- **Example**: `spark.cdm.perfops.batchSize=5000` for small rows

#### `spark.cdm.perfops.fetchSizeInRows`
- **Description**: Number of rows to fetch from Origin in each read operation
- **Type**: Integer
- **Default**: 1000
- **Recommended**: Based on row size:
  - Small rows (< 1KB): 1000-10000
  - Medium rows (1-10KB): 500-5000
  - Large rows (10-100KB): 100-1000
  - Very large rows (> 100KB): 10-100
- **Usage**: Controls how many rows are read from Cassandra in one query. Larger fetch = fewer queries = better throughput.
- **Memory Impact**: fetchSize × avg_row_size = memory per fetch
- **Relationship**: fetchSize should be >= batchSize for efficiency
- **Example**: `spark.cdm.perfops.fetchSizeInRows=5000` for small-medium rows

---

## Connection Configuration Parameters

### Origin (Cassandra) Connection

#### `spark.cdm.connect.origin.host`
- **Description**: Hostname or IP address of the origin Cassandra cluster
- **Type**: String
- **Required**: Yes
- **Example**: `spark.cdm.connect.origin.host=cassandra.example.com`

#### `spark.cdm.connect.origin.port`
- **Description**: Port number for CQL connections
- **Type**: Integer
- **Default**: 9042
- **Example**: `spark.cdm.connect.origin.port=9042`

#### `spark.cdm.connect.origin.username`
- **Description**: Username for authentication
- **Type**: String
- **Example**: `spark.cdm.connect.origin.username=cassandra`

#### `spark.cdm.connect.origin.password`
- **Description**: Password for authentication
- **Type**: String
- **Example**: `spark.cdm.connect.origin.password=password`

### Target (YugabyteDB YSQL) Connection

#### `spark.cdm.connect.target.yugabyte.host`
- **Description**: Hostname or IP address of the YugabyteDB cluster
- **Type**: String
- **Required**: Yes (for YSQL)
- **Example**: `spark.cdm.connect.target.yugabyte.host=yugabyte.example.com`

#### `spark.cdm.connect.target.yugabyte.port`
- **Description**: Port number for YSQL connections
- **Type**: Integer
- **Default**: 5433
- **Example**: `spark.cdm.connect.target.yugabyte.port=5433`

#### `spark.cdm.connect.target.yugabyte.database`
- **Description**: Database name (required for YSQL)
- **Type**: String
- **Required**: Yes (for YSQL)
- **Example**: `spark.cdm.connect.target.yugabyte.database=mydb`

#### `spark.cdm.connect.target.yugabyte.username`
- **Description**: Username for authentication
- **Type**: String
- **Example**: `spark.cdm.connect.target.yugabyte.username=yugabyte`

#### `spark.cdm.connect.target.yugabyte.password`
- **Description**: Password for authentication
- **Type**: String
- **Example**: `spark.cdm.connect.target.yugabyte.password=password`

### HikariCP Connection Pool Configuration

#### `spark.cdm.connect.target.yugabyte.pool.maxSize`
- **Description**: Maximum number of connections in the HikariCP pool
- **Type**: Integer
- **Default**: 10
- **Recommended**: 10-20
- **Example**: `spark.cdm.connect.target.yugabyte.pool.maxSize=10`

#### `spark.cdm.connect.target.yugabyte.pool.minSize`
- **Description**: Minimum number of idle connections in the HikariCP pool
- **Type**: Integer
- **Default**: 2
- **Recommended**: 2-5
- **Example**: `spark.cdm.connect.target.yugabyte.pool.minSize=2`

---

## Throughput Reference Tables

### Local Mode Throughput (--master "local[*]")

Rate limits apply to the **ENTIRE job** (not per executor).

| Rate Limit | Effective Throughput | Records/Hour | Records/Day | Executor Instances | Executor Memory | Driver Memory | Total Memory Needed |
|------------|---------------------|--------------|-------------|-------------------|-----------------|---------------|---------------------|
| 5,000 | 4,000-4,500/sec | 14-16 million | 345-380 million | 1-2 | 6G | 6G | ~20-24GB |
| 10,000 | 8,000-9,000/sec | 28-32 million | 690-780 million | 1-2 | 8G | 8G | ~24-32GB |
| 15,000 | 12,000-13,500/sec | 43-49 million | 1.0-1.2 billion | 2-4 | 8G | 8G | ~32-40GB |
| 20,000 | 16,000-18,000/sec | 57-65 million | 1.4-1.6 billion | 2-4 | 10G | 10G | ~40-48GB |

**Note**: In local mode, executor.instances may be ignored or used differently. Total memory ≈ driver.memory + executor.memory (not multiplied).

### Cluster Mode Throughput (--master "spark://master:port")

Rate limits apply **PER EXECUTOR** (per worker node).

| Rate Limit (per executor) | Executor Instances | Total Effective Throughput | Records/Hour | Records/Day | Memory per Executor | Driver Memory | Total Memory (all nodes) |
|---------------------------|-------------------|----------------------------|-------------|-------------|---------------------|---------------|--------------------------|
| 5,000 | 2 | 8,000-9,000/sec | 28-32 million | 690-780 million | 6G | 6G | ~20GB (2×6G+6G+overhead) |
| 5,000 | 4 | 16,000-18,000/sec | 57-65 million | 1.4-1.6 billion | 6G | 6G | ~40GB (4×6G+6G+overhead) |
| 5,000 | 8 | 32,000-36,000/sec | 115-130 million | 2.8-3.1 billion | 6G | 6G | ~80GB (8×6G+6G+overhead) |
| 10,000 | 2 | 16,000-18,000/sec | 57-65 million | 1.4-1.6 billion | 8G | 8G | ~28GB (2×8G+8G+overhead) |
| 10,000 | 4 | 32,000-36,000/sec | 115-130 million | 2.8-3.1 billion | 8G | 8G | ~56GB (4×8G+8G+overhead) |
| 10,000 | 8 | 64,000-72,000/sec | 230-259 million | 5.5-6.2 billion | 8G | 8G | ~112GB (8×8G+8G+overhead) |
| 15,000 | 4 | 48,000-54,000/sec | 173-194 million | 4.1-4.7 billion | 10G | 10G | ~70GB (4×10G+10G+overhead) |
| 20,000 | 4 | 64,000-72,000/sec | 230-259 million | 5.5-6.2 billion | 12G | 12G | ~84GB (4×12G+12G+overhead) |

**Formula**: Total Throughput = (Rate Limit × Executor Instances) × 0.8 to 0.9 (accounting for overhead)

---

## Memory Requirements Reference

### Memory Calculation Formula

```
Total Spark Memory = (Executor Memory × Executor Instances) + Driver Memory
Additional Overhead = JVM Overhead (10-20%) + OS (2-4GB) + Network Buffers (1-2GB) + Other (2-4GB)
Minimum Machine Memory = Total Spark Memory + Additional Overhead
Recommended Machine Memory = Minimum + 20% safety margin
```

### Memory Requirements by Configuration

| Executor Instances | Executor Memory | Driver Memory | Total Spark Memory | Minimum Machine Memory | Recommended Machine Memory |
|-------------------|-----------------|---------------|-------------------|----------------------|---------------------------|
| 1 | 6G | 6G | 12G | ~20GB | 24GB |
| 2 | 6G | 6G | 18G | ~28GB | 32GB |
| 4 | 6G | 6G | 30G | ~40GB | 48GB |
| 4 | 8G | 8G | 40G | ~52GB | 64GB |
| 4 | 10G | 10G | 50G | ~64GB | 80GB |
| 8 | 6G | 6G | 54G | ~70GB | 84GB |
| 8 | 8G | 8G | 72G | ~92GB | 112GB |

### Local Mode Memory Notes

In local mode, executors run in the same JVM as the driver, so actual memory usage is typically:
- **Local Mode**: Total memory ≈ driver.memory + executor.memory (not multiplied by instances)
- Example: 4 instances × 6G + 6G driver = ~12-16GB actual usage (not 30GB)

---

## Quick Configuration Examples

### Example 1: Small Table Migration (Local Mode)
**Use Case**: Migrating a 1-5GB table on a single machine

```properties
# Spark Configuration
spark.executor.cores=4
spark.executor.instances=2
spark.executor.memory=6G
spark.driver.memory=6G

# CDM Performance
spark.cdm.perfops.numParts=200
spark.cdm.perfops.ratelimit.origin=5000
spark.cdm.perfops.ratelimit.target=5000
spark.cdm.perfops.batchSize=5000
spark.cdm.perfops.fetchSizeInRows=5000
```

**Expected Throughput**: ~4,000-4,500 records/sec = 14-16 million/hour  
**Memory Required**: ~24GB machine

### Example 2: Medium Table Migration (Local Mode)
**Use Case**: Migrating a 10-50GB table on a single machine

```properties
# Spark Configuration
spark.executor.cores=4
spark.executor.instances=4
spark.executor.memory=8G
spark.driver.memory=8G

# CDM Performance
spark.cdm.perfops.numParts=1000
spark.cdm.perfops.ratelimit.origin=10000
spark.cdm.perfops.ratelimit.target=10000
spark.cdm.perfops.batchSize=2000
spark.cdm.perfops.fetchSizeInRows=2000
```

**Expected Throughput**: ~8,000-9,000 records/sec = 28-32 million/hour  
**Memory Required**: ~48GB machine

### Example 3: Large Table Migration (Cluster Mode)
**Use Case**: Migrating a 100GB+ table on a Spark cluster

```properties
# Spark Configuration
spark.executor.cores=4
spark.executor.instances=8
spark.executor.memory=10G
spark.driver.memory=10G

# CDM Performance
spark.cdm.perfops.numParts=10000
spark.cdm.perfops.ratelimit.origin=15000
spark.cdm.perfops.ratelimit.target=15000
spark.cdm.perfops.batchSize=1000
spark.cdm.perfops.fetchSizeInRows=1000
```

**Expected Throughput**: ~96,000-108,000 records/sec = 345-389 million/hour  
**Memory Required**: ~112GB total across all nodes

### Example 4: High-Throughput Migration (Cluster Mode)
**Use Case**: Maximum throughput migration with large cluster

```properties
# Spark Configuration
spark.executor.cores=8
spark.executor.instances=16
spark.executor.memory=12G
spark.driver.memory=12G

# CDM Performance
spark.cdm.perfops.numParts=20000
spark.cdm.perfops.ratelimit.origin=20000
spark.cdm.perfops.ratelimit.target=20000
spark.cdm.perfops.batchSize=5000
spark.cdm.perfops.fetchSizeInRows=5000
```

**Expected Throughput**: ~256,000-288,000 records/sec = 922 million-1.04 billion/hour  
**Memory Required**: ~240GB total across all nodes

---

## Performance Tuning Tips

1. **Start Conservative**: Begin with lower rate limits (5000) and gradually increase while monitoring cluster load.

2. **Monitor Bottlenecks**: Throughput is limited by the smallest of:
   - Origin read rate limit
   - Target write rate limit
   - Network bandwidth
   - Database cluster capacity

3. **Optimize Batch/Fetch Sizes**: 
   - Small rows: Use large batch/fetch sizes (1000-5000)
   - Large rows: Use smaller batch/fetch sizes (10-100)

4. **Adjust numParts**: 
   - Aim for ~10MB per partition
   - Too few = less parallelism
   - Too many = more overhead

5. **Use Cluster Mode**: For large migrations, cluster mode provides 4x+ throughput improvement.

6. **Memory Planning**: Always leave 20-30% memory headroom for OS, JVM overhead, and other processes.

---

## Additional Resources

- **Property Files**: 
  - `yugabyte-ysql-migration.properties` - YSQL migration template
  - `yugabyte-ycql-migration.properties` - YCQL migration template
  - `migration.properties.template` - Generic template

- **Documentation**: See `README.md` for general usage instructions

- **Troubleshooting**: Monitor cluster metrics and adjust parameters based on observed performance

