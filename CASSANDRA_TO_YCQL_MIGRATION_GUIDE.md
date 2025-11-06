# Cassandra to Yugabyte YCQL Migration Guide

This guide explains how to migrate data from DataStax Cassandra to YugabyteDB YCQL (Cassandra-compatible interface).

## Overview

YugabyteDB YCQL uses the same CQL protocol as Cassandra, so the migration uses the **standard Cassandra-to-Cassandra migration** approach, not the Yugabyte YSQL-specific migration.

### ✅ **Same DataStax Driver Used**

**Yes, you can use the same DataStax driver to connect to Yugabyte YCQL!** 

The CDM codebase uses:
- **Spark Cassandra Connector** (`com.datastax.spark.connector.cql.CassandraConnector`) for both origin and target
- **DataStax Java Driver** (version 4.19.0) underneath the connector
- Both connections use the same `CassandraConnector` class - no special handling needed for YCQL

Since Yugabyte YCQL is **fully Cassandra-compatible** at the CQL protocol level, the DataStax driver works seamlessly with it. You just need to:
1. Point to your Yugabyte YCQL host (port 9042)
2. Use standard Cassandra connection properties
3. The same driver handles both connections automatically

## Key Differences

| Aspect | YSQL Migration | YCQL Migration |
|--------|---------------|----------------|
| **Port** | 5433 (PostgreSQL) | 9042 (CQL) |
| **Main Class** | `com.datastax.cdm.job.YugabyteMigrate` | `com.datastax.cdm.job.Migrate` |
| **Target Properties** | `spark.cdm.connect.target.yugabyte.*` | `spark.cdm.connect.target.*` |
| **Protocol** | PostgreSQL | CQL (Cassandra) |

## Properties File

Use the provided `cassandra-to-ycql-migration.properties` file and update the following values:

1. **Origin (Cassandra) Connection:**
   - `spark.cdm.connect.origin.host` - Your Cassandra host
   - `spark.cdm.connect.origin.port` - Usually 9042
   - `spark.cdm.connect.origin.username` - Your Cassandra username
   - `spark.cdm.connect.origin.password` - Your Cassandra password

2. **Target (Yugabyte YCQL) Connection:**
   - `spark.cdm.connect.target.host` - Your Yugabyte YCQL host
   - `spark.cdm.connect.target.port` - Usually 9042 (YCQL port)
   - `spark.cdm.connect.target.username` - Your Yugabyte username
   - `spark.cdm.connect.target.password` - Your Yugabyte password

3. **Schema:**
   - `spark.cdm.schema.origin.keyspaceTable` - Source keyspace.table
   - `spark.cdm.schema.target.keyspaceTable` - Target keyspace.table

## Migration Command

### Basic Command

```bash
spark-submit --properties-file cassandra-to-ycql-migration.properties \
--conf spark.cdm.schema.origin.keyspaceTable="customer_datastore.customer_mtrc_by_lpid" \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.Migrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> ycql_migration_$(date +%Y%m%d_%H_%M).txt
```

### With Run Tracking

```bash
spark-submit --properties-file cassandra-to-ycql-migration.properties \
--conf spark.cdm.schema.origin.keyspaceTable="customer_datastore.customer_mtrc_by_lpid" \
--conf spark.cdm.trackRun=true \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.Migrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> ycql_migration_$(date +%Y%m%d_%H_%M).txt
```

### Performance-Tuned Command

```bash
spark-submit --properties-file cassandra-to-ycql-migration.properties \
--conf spark.cdm.schema.origin.keyspaceTable="customer_datastore.customer_mtrc_by_lpid" \
--conf spark.cdm.perfops.numParts=1000 \
--conf spark.cdm.perfops.batchSize=5000 \
--conf spark.cdm.perfops.fetchSizeInRows=5000 \
--conf spark.cdm.perfops.ratelimit.origin=10000 \
--conf spark.cdm.perfops.ratelimit.target=10000 \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.Migrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> ycql_migration_$(date +%Y%m%d_%H_%M).txt
```

## SSL Configuration

### SSL is Disabled by Default ✅

**SSL is disabled by default** in CDM. The code defaults `spark.cdm.connect.origin.tls.enabled` and `spark.cdm.connect.target.tls.enabled` to `false`.

To bypass SSL (which is the default):
- **Leave all TLS properties commented out** (recommended - uses default "false")
- OR explicitly set `spark.cdm.connect.origin.tls.enabled=false` and `spark.cdm.connect.target.tls.enabled=false`

**You can safely bypass SSL by leaving the TLS properties commented out.**

### To Enable SSL (if required)

If your clusters require SSL, uncomment and configure the TLS properties in the properties file:

```properties
# Origin SSL
spark.cdm.connect.origin.tls.enabled=true
spark.cdm.connect.origin.tls.trustStore.path=/path/to/truststore.jks
spark.cdm.connect.origin.tls.trustStore.password=your_password
spark.cdm.connect.origin.tls.trustStore.type=JKS

# Target SSL
spark.cdm.connect.target.tls.enabled=true
spark.cdm.connect.target.tls.trustStore.path=/path/to/truststore.jks
spark.cdm.connect.target.tls.trustStore.password=your_password
spark.cdm.connect.target.tls.trustStore.type=JKS
```

## Validation Command

To validate the migrated data:

```bash
spark-submit --properties-file cassandra-to-ycql-migration.properties \
--conf spark.cdm.schema.origin.keyspaceTable="customer_datastore.customer_mtrc_by_lpid" \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.DiffData cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> ycql_validation_$(date +%Y%m%d_%H_%M).txt
```

## Monitoring

### Check Logs

```bash
# View the log file
tail -f ycql_migration_*.txt

# Check for errors
grep ERROR ycql_migration_*.txt

# Check for validation errors
grep ERROR ycql_validation_*.txt
```

### Spark UI

If running locally, open http://localhost:4040 in your browser to monitor the Spark job.

## Troubleshooting

### Connection Issues

1. **Verify connectivity:**
   ```bash
   # Test Cassandra connection
   cqlsh your-cassandra-host 9042 -u username -p password
   
   # Test Yugabyte YCQL connection (same command as Cassandra)
   cqlsh your-yugabyte-host 9042 -u username -p password
   ```

2. **Check firewall/network:** Ensure port 9042 is accessible for both clusters

3. **Verify credentials:** Double-check username and password in properties file

### SSL Issues

If you encounter SSL-related errors:
- Ensure TLS properties are commented out (SSL disabled)
- Or properly configure truststore/keystore paths if SSL is required

### Performance Issues

- Adjust `spark.cdm.perfops.numParts` based on your data size
- Tune `spark.cdm.perfops.ratelimit.origin` and `spark.cdm.perfops.ratelimit.target`
- Increase Spark memory if needed: `--driver-memory` and `--executor-memory`

## Summary

- **Use standard Cassandra-to-Cassandra migration** (`com.datastax.cdm.job.Migrate`)
- **YCQL uses port 9042** (same as Cassandra)
- **Use `spark.cdm.connect.target.*` properties** (not `spark.cdm.connect.target.yugabyte.*`)
- **SSL is disabled by default** - leave TLS properties commented to bypass SSL
- **Yugabyte YCQL is fully Cassandra-compatible** at the protocol level

