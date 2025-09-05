# YugabyteDB Migration with Cassandra Data Migrator

This document explains how to use the modified Cassandra Data Migrator to migrate data from DataStax Cassandra to YugabyteDB YSQL (PostgreSQL-compatible).

## Overview

The modified CDM adds support for YugabyteDB YSQL as a target, allowing you to:
- Read data from Cassandra using CDM's powerful token-based scanning
- Automatically map Cassandra data types to PostgreSQL data types
- Write data to YugabyteDB YSQL using standard SQL INSERT/UPDATE statements

## Prerequisites

1. **DataStax Cassandra** cluster (source)
2. **YugabyteDB** cluster with YSQL enabled (target)
3. **Java 8+** and **Spark 3.x**
4. **PostgreSQL JDBC driver** (included in dependencies)

## Setup

### 1. Create Target Table in YugabyteDB

First, create your target table in YugabyteDB YSQL:

```sql
-- Connect to YugabyteDB YSQL
psql -h your-yugabyte-host -p 5433 -U yugabyte -d yugabyte

-- Create the target table
CREATE TABLE customer (
    customer_id UUID PRIMARY KEY,
    name TEXT,
    email TEXT,
    created_date TIMESTAMP,
    region_id INTEGER,
    -- Add other columns based on your Cassandra schema
);
```

### 2. Configure Migration

Create a configuration file `yugabyte-migrate.properties`:

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

# Schema
spark.cdm.schema.origin.keyspaceTable=your_keyspace.customer
spark.cdm.schema.target.keyspaceTable=your_keyspace.customer

# Performance
spark.cdm.perfops.numParts=20
spark.cdm.perfops.fetchSize=1000
```

### 3. Run Migration

```bash
# Build the project
mvn clean package

# Run YugabyteDB migration
spark-submit \
  --class com.datastax.cdm.job.YugabyteMigrate \
  --properties-file yugabyte-migrate.properties \
  --packages org.postgresql:postgresql:42.2.24 \
  target/cassandra-data-migrator-*.jar
```

## Data Type Mapping

The system automatically maps Cassandra data types to PostgreSQL data types:

| Cassandra Type | PostgreSQL Type | Java Type |
|----------------|-----------------|-----------|
| text, varchar  | text            | String    |
| int            | integer         | Integer   |
| bigint         | bigint          | Long      |
| float          | real            | Float     |
| double         | double precision| Double    |
| boolean        | boolean         | Boolean   |
| uuid           | uuid            | UUID      |
| timestamp      | timestamp       | LocalDateTime |
| blob           | bytea           | byte[]    |
| list<>, set<>, map<> | text | String (JSON) |

## Features

### 1. Automatic Schema Discovery
- Discovers table structure from YugabyteDB
- Maps column names and data types automatically
- Handles primary key constraints

### 2. Data Type Conversion
- Converts Cassandra data types to PostgreSQL equivalents
- Handles collections as JSON strings
- Preserves UUID and timestamp formats

### 3. Upsert Operations
- Uses PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` for upserts
- Handles primary key conflicts gracefully
- Updates non-primary key columns on conflict

### 4. Performance Optimization
- Parallel processing with configurable partitions
- Rate limiting for both source and target
- Batch processing for better throughput

## Configuration Options

### Connection Parameters
- `spark.cdm.connect.target.type=yugabyte` - Enable YugabyteDB target
- `spark.cdm.connect.target.yugabyte.host` - YugabyteDB host
- `spark.cdm.connect.target.yugabyte.port` - YugabyteDB YSQL port (default: 5433)
- `spark.cdm.connect.target.yugabyte.database` - Database name
- `spark.cdm.connect.target.yugabyte.username` - Username
- `spark.cdm.connect.target.yugabyte.password` - Password

### Performance Tuning
- `spark.cdm.perfops.numParts` - Number of parallel partitions (default: 10)
- `spark.cdm.perfops.fetchSize` - Rows per fetch (default: 1000)
- `spark.cdm.perfops.rateLimit.origin` - Origin rate limit (default: 1000)
- `spark.cdm.perfops.rateLimit.target` - Target rate limit (default: 1000)

### Filtering
- `spark.cdm.filter.cassandra.partition.min` - Minimum token value
- `spark.cdm.filter.cassandra.partition.max` - Maximum token value
- `spark.cdm.filter.java.token.percent` - Token coverage percentage

## Example: Migrating 13M Row Customer Table

```properties
# For a large customer table
spark.cdm.schema.origin.keyspaceTable=production.customer
spark.cdm.schema.target.keyspaceTable=production.customer
spark.cdm.perfops.numParts=50
spark.cdm.perfops.fetchSize=2000
spark.cdm.perfops.rateLimit.origin=2000
spark.cdm.perfops.rateLimit.target=1500
```

## Monitoring and Troubleshooting

### 1. Check Migration Progress
```bash
# Monitor Spark UI at http://localhost:4040
# Check YugabyteDB for incoming data
psql -h your-yugabyte-host -p 5433 -U yugabyte -d yugabyte -c "SELECT COUNT(*) FROM customer;"
```

### 2. Common Issues

**Connection Issues:**
- Verify YugabyteDB YSQL is running on port 5433
- Check firewall settings
- Verify credentials

**Data Type Issues:**
- Ensure target table schema matches expected types
- Check for unsupported Cassandra types
- Review data type mapping logs

**Performance Issues:**
- Increase `numParts` for more parallelism
- Adjust `fetchSize` based on row size
- Monitor rate limits

### 3. Logs
- Check Spark driver logs for connection issues
- Monitor executor logs for data processing
- Review YugabyteDB logs for write performance

## Limitations

1. **Collections**: Cassandra collections (list, set, map) are stored as JSON strings
2. **UDTs**: User-defined types are stored as JSON strings
3. **TTL/Writetime**: Not preserved in YugabyteDB
4. **Counters**: Converted to regular integers
5. **Schema Changes**: Manual schema updates required

## Best Practices

1. **Test First**: Run migration on a small dataset first
2. **Monitor Performance**: Watch both Cassandra and YugabyteDB metrics
3. **Backup**: Always backup your data before migration
4. **Validate**: Compare row counts and sample data after migration
5. **Tune**: Adjust performance parameters based on your data size

## Support

For issues or questions:
1. Check the logs for error messages
2. Verify configuration parameters
3. Test connectivity to both Cassandra and YugabyteDB
4. Review data type mappings for your specific schema

