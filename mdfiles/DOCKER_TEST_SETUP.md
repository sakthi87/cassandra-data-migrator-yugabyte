# Docker Test Setup for CDM Migration

## ✅ Containers Installed and Running

Both Cassandra and YugabyteDB containers are now running and ready for testing!

---

## 🐳 Container Status

### YugabyteDB Container
- **Container Name:** `yugabyte`
- **Status:** ✅ Running and Ready
- **Image:** `yugabytedb/yugabyte:2025.2.0.0-b131`

### Cassandra Container
- **Container Name:** `cassandra`
- **Status:** ✅ Running (may still be initializing - wait 30-60 seconds)
- **Image:** `cassandra:latest`

---

## 🔌 Connection Details

### YugabyteDB (Target)

| Service | Port | Connection String |
|---------|------|-------------------|
| **YSQL (PostgreSQL)** | `5433` | `jdbc:postgresql://localhost:5433/yugabyte?user=yugabyte&password=yugabyte` |
| **YCQL (Cassandra)** | `9042` | `localhost:9042` |
| **YugabyteDB UI** | `15433` | `http://localhost:15433` |
| **Master UI** | `7001` | `http://localhost:7001` (mapped from 7000) |

**Default Credentials:**
- Username: `yugabyte`
- Password: `yugabyte`
- Database: `yugabyte`

### Cassandra (Source)

| Service | Port | Connection String |
|---------|------|-------------------|
| **CQL Native** | `9043` | `localhost:9043` (mapped from 9042) |
| **Thrift** | `9160` | `localhost:9160` |
| **JMX** | `7199` | `localhost:7199` |

**Default Credentials:**
- Username: `cassandra`
- Password: `cassandra`

**Note:** Port `9043` is used instead of `9042` to avoid conflict with YugabyteDB YCQL.

---

## 🧪 Test Commands

### 1. Connect to YugabyteDB YSQL

```bash
# Using Docker exec
docker exec -it yugabyte bash -c '/home/yugabyte/bin/ysqlsh --echo-queries --host $(hostname) -U yugabyte -d yugabyte'

# Or from host (if ysqlsh is installed locally)
ysqlsh -h localhost -p 5433 -U yugabyte -d yugabyte
```

### 2. Connect to YugabyteDB YCQL

```bash
docker exec -it yugabyte bash -c '/home/yugabyte/bin/ycqlsh $(hostname) 9042 -u cassandra'
```

### 3. Connect to Cassandra CQL

```bash
# Wait for Cassandra to fully start (check logs first)
docker logs cassandra --tail 20

# Then connect
docker exec -it cassandra cqlsh localhost 9042
```

### 4. Check Container Status

```bash
# Check all containers
docker ps | grep -E "(yugabyte|cassandra)"

# Check YugabyteDB status
docker exec yugabyte yugabyted status

# Check Cassandra status (wait for it to be ready)
docker exec cassandra nodetool status
```

---

## 📝 Sample Test Migration Setup

### Step 1: Create Test Table in Cassandra

```bash
# Connect to Cassandra
docker exec -it cassandra cqlsh localhost 9042

# In cqlsh, run:
CREATE KEYSPACE test_keyspace WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE test_keyspace;

CREATE TABLE test_table (
    id UUID PRIMARY KEY,
    name TEXT,
    email TEXT,
    age INT,
    created_at TIMESTAMP
);

# Insert some test data
INSERT INTO test_table (id, name, email, age, created_at) VALUES (uuid(), 'John Doe', 'john@example.com', 30, toTimestamp(now()));
INSERT INTO test_table (id, name, email, age, created_at) VALUES (uuid(), 'Jane Smith', 'jane@example.com', 25, toTimestamp(now()));
INSERT INTO test_table (id, name, email, age, created_at) VALUES (uuid(), 'Bob Johnson', 'bob@example.com', 35, toTimestamp(now()));

# Verify data
SELECT * FROM test_table;
```

### Step 2: Create Target Table in YugabyteDB

```bash
# Connect to YugabyteDB YSQL
docker exec -it yugabyte bash -c '/home/yugabyte/bin/ysqlsh --echo-queries --host $(hostname) -U yugabyte -d yugabyte'

# In ysqlsh, run:
CREATE DATABASE test_keyspace;

\c test_keyspace

CREATE TABLE test_table (
    id UUID PRIMARY KEY,
    name TEXT,
    email TEXT,
    age INT,
    created_at TIMESTAMP,
    -- Optional audit fields
    created_by TEXT,
    migration_date DATE,
    source_system TEXT
);
```

### Step 3: Configure CDM Properties File

Create or update `yugabyte-ysql-migration.properties`:

```properties
# Origin (Cassandra)
spark.cdm.connect.origin.host=localhost
spark.cdm.connect.origin.port=9043
spark.cdm.connect.origin.username=cassandra
spark.cdm.connect.origin.password=cassandra

# Target (YugabyteDB)
spark.cdm.connect.target.yugabyte.host=localhost
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=test_keyspace
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=yugabyte

# Schema
spark.cdm.schema.origin.keyspaceTable=test_keyspace.test_table
spark.cdm.schema.target.keyspaceTable=test_keyspace.test_table

# High-Performance Settings (Phase 1+2)
spark.cdm.connect.target.yugabyte.batchSize=25
spark.cdm.connect.target.yugabyte.rewriteBatchedInserts=true
spark.cdm.connect.target.yugabyte.loadBalance=true
spark.cdm.connect.target.yugabyte.pool.maxSize=20
spark.cdm.connect.target.yugabyte.pool.minSize=5

# Performance
spark.cdm.perfops.numParts=100
spark.cdm.perfops.batchSize=5
spark.cdm.perfops.ratelimit.origin=10000
spark.cdm.perfops.ratelimit.target=10000
spark.cdm.perfops.fetchSizeInRows=1000

# Optional: Audit Fields
# spark.cdm.feature.constantColumns.names=created_by,migration_date,source_system
# spark.cdm.feature.constantColumns.values='CDM_MIGRATION','2024-12-17','CASSANDRA_DOCKER'
```

### Step 4: Run Migration

```bash
cd /Users/subhalakshmiraj/Documents/cassandra-data-migrator-main

spark-submit \
  --properties-file yugabyte-ysql-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="test_keyspace.test_table" \
  --master "local[*]" \
  --driver-memory 2G \
  --executor-memory 2G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

### Step 5: Verify Migration

```bash
# Connect to YugabyteDB and verify data
docker exec -it yugabyte bash -c '/home/yugabyte/bin/ysqlsh --echo-queries --host $(hostname) -U yugabyte -d test_keyspace'

# In ysqlsh:
SELECT * FROM test_table;
SELECT COUNT(*) FROM test_table;
```

---

## 🔍 Troubleshooting

### Check Container Logs

```bash
# YugabyteDB logs
docker logs yugabyte --tail 50

# Cassandra logs
docker logs cassandra --tail 50
```

### Restart Containers

```bash
# Stop containers
docker stop yugabyte cassandra

# Start containers
docker start yugabyte cassandra

# Or remove and recreate
docker rm -f yugabyte cassandra
# Then re-run the docker run commands from above
```

### Verify Ports Are Available

```bash
# Check what's using ports
lsof -i :5433  # YugabyteDB YSQL
lsof -i :9042  # YugabyteDB YCQL
lsof -i :9043  # Cassandra CQL
lsof -i :7000  # AirPlay (macOS) or YugabyteDB Master
```

### Wait for Cassandra to Be Ready

Cassandra takes 30-60 seconds to fully start. Check readiness:

```bash
# Keep checking until you see "UN" (Up Normal)
docker exec cassandra nodetool status

# Or check logs for "Starting listening for CQL clients"
docker logs cassandra | grep "Starting listening"
```

---

## 📊 Monitoring

### YugabyteDB UI
- **URL:** http://localhost:15433
- **Features:** Cluster status, metrics, query performance

### Cassandra Monitoring
- **JMX:** `localhost:7199`
- **Nodetool:** `docker exec cassandra nodetool <command>`

---

## 🧹 Cleanup

To stop and remove containers:

```bash
# Stop containers
docker stop yugabyte cassandra

# Remove containers (data will be lost)
docker rm yugabyte cassandra

# Remove images (optional)
docker rmi yugabytedb/yugabyte:2025.2.0.0-b131 cassandra:latest
```

---

## 📚 References

- **YugabyteDB Quick Start:** https://docs.yugabyte.com/stable/quick-start/docker/
- **Cassandra Docker:** https://hub.docker.com/_/cassandra
- **CDM Documentation:** See `README.md` and `AUDIT_FIELDS_GUIDE.md`

---

## ✅ Quick Verification Checklist

- [ ] YugabyteDB container is running (`docker ps | grep yugabyte`)
- [ ] YugabyteDB is ready (`docker exec yugabyte yugabyted status`)
- [ ] Cassandra container is running (`docker ps | grep cassandra`)
- [ ] Cassandra is ready (`docker exec cassandra nodetool status` shows "UN")
- [ ] Can connect to YugabyteDB YSQL (`docker exec -it yugabyte ysqlsh ...`)
- [ ] Can connect to Cassandra CQL (`docker exec -it cassandra cqlsh ...`)
- [ ] Test tables created in both databases
- [ ] CDM properties file configured correctly
- [ ] Ready to run migration!

---

**Last Updated:** December 17, 2024
**Containers Created:** Both containers are running and ready for testing!

