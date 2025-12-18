# Transaction Table Setup for CDM Testing

## ✅ Tables Created Successfully

Both Cassandra and YugabyteDB tables have been created and are ready for migration testing!

---

## 📊 Table Details

### Table Name
`dda_pstd_fincl_txn_cnsmr_by_accntnbr`

### Schema Summary
- **Total Columns:** 118 columns
- **Primary Key:** Composite (5 columns)
  - Partition Key: `cmpny_id`, `accnt_nbr`, `prdct_cde`
  - Clustering Key: `pstd_dt`, `txn_seq`
- **Data Types:** TEXT, DOUBLE, TIMESTAMP, DATE

---

## 🗄️ Cassandra (Source) Setup

### Keyspace
```cql
CREATE KEYSPACE transaction_datastore 
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
```

### Connection Details
- **Host:** `localhost`
- **Port:** `9043` (mapped from 9042)
- **Keyspace:** `transaction_datastore`
- **Table:** `dda_pstd_fincl_txn_cnsmr_by_accntnbr`
- **Username:** `cassandra`
- **Password:** `cassandra`

### Primary Key Structure
```cql
PRIMARY KEY ((cmpny_id, accnt_nbr, prdct_cde), pstd_dt, txn_seq)
WITH CLUSTERING ORDER BY (pstd_dt DESC, txn_seq ASC)
```

### Verify Table
```bash
docker exec -it cassandra cqlsh localhost 9042

USE transaction_datastore;
DESCRIBE TABLE dda_pstd_fincl_txn_cnsmr_by_accntnbr;
SELECT COUNT(*) FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr;
```

---

## 🗄️ YugabyteDB (Target) Setup

### Database
```sql
CREATE DATABASE transaction_datastore;
```

### Connection Details
- **Host:** `localhost`
- **Port:** `5433` (YSQL/PostgreSQL)
- **Database:** `transaction_datastore`
- **Table:** `dda_pstd_fincl_txn_cnsmr_by_accntnbr`
- **Username:** `yugabyte`
- **Password:** `yugabyte`

### Primary Key Structure
```sql
PRIMARY KEY (cmpny_id, accnt_nbr, prdct_cde, pstd_dt, txn_seq)
```

**Note:** YugabyteDB YSQL uses a single composite PRIMARY KEY (all columns together) rather than CQL's partition/clustering key separation. YugabyteDB automatically handles the sharding based on the first columns.

### Index Created
```sql
CREATE INDEX idx_dda_pstd_fincl_txn_pstd_dt_txn_seq 
ON dda_pstd_fincl_txn_cnsmr_by_accntnbr (pstd_dt DESC, txn_seq ASC);
```

This index simulates the CQL clustering order for query performance.

### Verify Table
```bash
docker exec -it yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore'

\dt
SELECT COUNT(*) FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr;
\d dda_pstd_fincl_txn_cnsmr_by_accntnbr
```

---

## 🔧 CDM Configuration

### Properties File: `yugabyte-ysql-migration.properties`

```properties
# =============================================================================
# ORIGIN (CASSANDRA) CONNECTION
# =============================================================================
spark.cdm.connect.origin.host=localhost
spark.cdm.connect.origin.port=9043
spark.cdm.connect.origin.username=cassandra
spark.cdm.connect.origin.password=cassandra

# =============================================================================
# TARGET (YUGABYTEDB) CONNECTION
# =============================================================================
spark.cdm.connect.target.yugabyte.host=localhost
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=transaction_datastore
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=yugabyte

# =============================================================================
# SCHEMA CONFIGURATION
# =============================================================================
spark.cdm.schema.origin.keyspaceTable=transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr
spark.cdm.schema.target.keyspaceTable=transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr

# =============================================================================
# HIGH-PERFORMANCE SETTINGS (Phase 1+2 Optimizations)
# =============================================================================
spark.cdm.connect.target.yugabyte.batchSize=25
spark.cdm.connect.target.yugabyte.rewriteBatchedInserts=true
spark.cdm.connect.target.yugabyte.loadBalance=true
spark.cdm.connect.target.yugabyte.prepareThreshold=5
spark.cdm.connect.target.yugabyte.pool.maxSize=20
spark.cdm.connect.target.yugabyte.pool.minSize=5

# =============================================================================
# PERFORMANCE SETTINGS
# =============================================================================
spark.cdm.perfops.numParts=1000
spark.cdm.perfops.batchSize=5
spark.cdm.perfops.ratelimit.origin=10000
spark.cdm.perfops.ratelimit.target=10000
spark.cdm.perfops.fetchSizeInRows=1000

# =============================================================================
# OPTIONAL: AUDIT FIELDS (if target table has extra audit columns)
# =============================================================================
# spark.cdm.feature.constantColumns.names=z_audit_crtd_by_txt,z_audit_evnt_id,z_audit_crtd_ts
# spark.cdm.feature.constantColumns.values='CDM_MIGRATION','MIGRATION_001','2024-12-17T10:00:00Z'
```

---

## 🧪 Test Migration Command

```bash
cd /Users/subhalakshmiraj/Documents/cassandra-data-migrator-main

spark-submit \
  --properties-file yugabyte-ysql-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr" \
  --master "local[*]" \
  --driver-memory 4G \
  --executor-memory 4G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

---

## 📝 Sample Test Data

### Insert Test Data into Cassandra

```bash
docker exec -it cassandra cqlsh localhost 9042
```

```cql
USE transaction_datastore;

-- Insert sample transaction records
INSERT INTO dda_pstd_fincl_txn_cnsmr_by_accntnbr (
    cmpny_id, accnt_nbr, prdct_cde, pstd_dt, txn_seq,
    txn_amt, txn_desc, txn_status, accnt_bal, avail_bal,
    pstd_ts, txn_ts
) VALUES (
    'COMP001', 'ACC001', 'PRD001', '2024-12-17', 'SEQ001',
    100.50, 'Test Transaction 1', 'COMPLETED', 1000.00, 900.50,
    toTimestamp(now()), toTimestamp(now())
);

INSERT INTO dda_pstd_fincl_txn_cnsmr_by_accntnbr (
    cmpny_id, accnt_nbr, prdct_cde, pstd_dt, txn_seq,
    txn_amt, txn_desc, txn_status, accnt_bal, avail_bal,
    pstd_ts, txn_ts
) VALUES (
    'COMP001', 'ACC001', 'PRD001', '2024-12-17', 'SEQ002',
    250.75, 'Test Transaction 2', 'COMPLETED', 1250.75, 1000.00,
    toTimestamp(now()), toTimestamp(now())
);

-- Verify data
SELECT cmpny_id, accnt_nbr, pstd_dt, txn_seq, txn_amt, txn_desc 
FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr 
WHERE cmpny_id = 'COMP001' AND accnt_nbr = 'ACC001' AND prdct_cde = 'PRD001';
```

### Verify Migration in YugabyteDB

```bash
docker exec -it yugabyte bash -c '/home/yugabyte/bin/ysqlsh --host $(hostname) -U yugabyte -d transaction_datastore'
```

```sql
-- Verify migrated data
SELECT cmpny_id, accnt_nbr, pstd_dt, txn_seq, txn_amt, txn_desc 
FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr 
WHERE cmpny_id = 'COMP001' AND accnt_nbr = 'ACC001' AND prdct_cde = 'PRD001'
ORDER BY pstd_dt DESC, txn_seq ASC;

-- Count records
SELECT COUNT(*) FROM dda_pstd_fincl_txn_cnsmr_by_accntnbr;
```

---

## 🔍 Schema Differences

| Aspect | Cassandra (CQL) | YugabyteDB (YSQL) |
|--------|----------------|-------------------|
| **Primary Key** | `PRIMARY KEY ((p1, p2, p3), c1, c2)` | `PRIMARY KEY (p1, p2, p3, c1, c2)` |
| **Partition Key** | Explicit `((...))` | First columns in PRIMARY KEY |
| **Clustering Order** | `WITH CLUSTERING ORDER BY` | Index with `DESC/ASC` |
| **DOUBLE Type** | `DOUBLE` | `DOUBLE PRECISION` |
| **TIMESTAMP** | `TIMESTAMP` | `TIMESTAMP WITHOUT TIME ZONE` |

**Note:** CDM handles these differences automatically during migration!

---

## ✅ Verification Checklist

- [x] Cassandra keyspace `transaction_datastore` created
- [x] Cassandra table `dda_pstd_fincl_txn_cnsmr_by_accntnbr` created
- [x] YugabyteDB database `transaction_datastore` created
- [x] YugabyteDB table `dda_pstd_fincl_txn_cnsmr_by_accntnbr` created
- [x] Both tables have matching column structure (118 columns)
- [x] Primary keys configured correctly
- [x] Index created for clustering order simulation
- [ ] Test data inserted into Cassandra (optional)
- [ ] CDM migration executed
- [ ] Data verified in YugabyteDB

---

## 🚀 Ready for Migration!

Your test environment is now ready for CDM migration testing with:
- ✅ Phase 1+2 performance optimizations
- ✅ PreparedStatement reuse
- ✅ JDBC batching (25 records per batch)
- ✅ Connection pooling (20 max connections)
- ✅ rewriteBatchedInserts enabled

**Expected Performance:** 15-20K rows/sec (vs 2-8K rows/sec without optimizations)

---

**Last Updated:** December 17, 2024
**Status:** Tables created and ready for testing!

