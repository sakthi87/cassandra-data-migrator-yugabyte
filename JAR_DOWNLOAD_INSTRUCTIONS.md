# Pre-built JAR Download Instructions

## ✅ JAR File Available on GitHub

**Location:** `target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar`

**Repository:** https://github.com/sakthi87/cassandra-data-migrator-yugabyte.git

**Latest Commit:** `188f7c2` - Includes pre-built JAR with all schema support changes

**Size:** 36MB

## Download Options

### Option 1: Clone Repository (Recommended)

```bash
git clone https://github.com/sakthi87/cassandra-data-migrator-yugabyte.git
cd cassandra-data-migrator-yugabyte
# JAR is in: target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

### Option 2: Download JAR Directly from GitHub

1. Go to: https://github.com/sakthi87/cassandra-data-migrator-yugabyte
2. Navigate to: `target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar`
3. Click "Download" or "View Raw"
4. Save the file

### Option 3: Use GitHub API

```bash
curl -L -o cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  https://github.com/sakthi87/cassandra-data-migrator-yugabyte/raw/main/target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## Verify JAR Contains Schema Support

After downloading, verify the JAR contains the schema support code:

```bash
# Extract and check for schema-related classes
jar -tf cassandra-data-migrator-5.5.2-SNAPSHOT.jar | grep -i "YugabyteTable\|YugabyteSession" | head -5

# Check for schema support methods (requires decompiling or checking logs)
# Run a test migration and check logs for:
# - "currentSchema: your_schema"
# - "INSERT INTO your_schema.table_name"
```

## What's Included in This JAR

✅ **Schema Support:**
- `currentSchema` parameter in JDBC connection URL
- Custom schema configuration via `spark.cdm.connect.target.yugabyte.schema`
- Enhanced schema detection (database.table vs schema.table)
- Auto-detection fallback

✅ **Performance Optimizations:**
- PreparedStatement reuse (Phase 1)
- JDBC batching with rewriteBatchedInserts (Phase 2)
- HikariCP connection pooling
- YugabyteDB Smart Driver optimizations

✅ **Audit Fields:**
- Constant columns feature
- Type-safe value parsing
- Support for all PostgreSQL data types

✅ **Tested Features:**
- Successfully tested with 100k+ records
- Verified with custom `transactions` schema
- All audit fields populated correctly

## Usage

```bash
spark-submit --properties-file transaction-test-audit.properties \
  --master "local[*]" \
  --driver-memory 4G \
  --executor-memory 4G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## Configuration Example

```properties
# Database
spark.cdm.connect.target.yugabyte.database=transaction_datastore

# Schema (REQUIRED for custom schemas)
spark.cdm.connect.target.yugabyte.schema=transactions

# Table (just table name - database and schema defined above)
spark.cdm.schema.target.keyspaceTable=your_table_name
```

## Verification

After running migration, check logs for:
1. ✅ `currentSchema: transactions (default schema for connection)`
2. ✅ `INSERT INTO transactions.your_table_name`
3. ✅ Successful migration completion

## Troubleshooting

**Issue: JAR not found after clone**
- The JAR is in `target/` directory
- Ensure you're in the repository root: `cd cassandra-data-migrator-yugabyte`
- Check: `ls -lh target/cassandra-data-migrator-*.jar`

**Issue: Schema not working**
- Verify properties file has: `spark.cdm.connect.target.yugabyte.schema=your_schema`
- Check logs for `currentSchema:` message
- Verify table exists in the specified schema

**Issue: Need to rebuild**
- If you have Maven available: `mvn clean package -DskipTests`
- Otherwise, use the pre-built JAR from GitHub

