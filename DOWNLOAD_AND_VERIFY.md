# Download and Verify Schema Support

## Quick Start for Restricted Environment

### Step 1: Download from GitHub

```bash
# Clone the repository
git clone https://github.com/sakthi87/cassandra-data-migrator-yugabyte.git
cd cassandra-data-migrator-yugabyte

# Verify you're on the latest commit
git log --oneline -1
# Expected: 1edbb4d Implement and test custom schema support for YugabyteDB YSQL
```

### Step 2: Verify Schema Support Code is Present

Run the verification script:
```bash
chmod +x verify_schema_support.sh
./verify_schema_support.sh
```

**Expected Output:**
```
=== Verifying Schema Support Changes ===

1. Checking KnownProperties.java...
   ✅ TARGET_YUGABYTE_SCHEMA property found

2. Checking YugabyteSession.java...
   ✅ currentSchema parameter found

3. Checking YugabyteTable.java...
   ✅ determineSchemaName method found
   ✅ getSchemaName method found
   ✅ database name detection found

4. Checking YugabyteUpsertStatement.java...
   ✅ getSchemaName() usage found

=== Verification Complete ===
```

### Step 3: Build the JAR

```bash
mvn clean package -DskipTests
```

**Important:** The JAR must be rebuilt after downloading to include the schema support changes.

### Step 4: Verify Configuration

Check that your properties file includes:
```properties
# Schema configuration (REQUIRED for custom schemas)
spark.cdm.connect.target.yugabyte.schema=your_schema_name

# Target table (can be just table name when schema is defined above)
spark.cdm.schema.target.keyspaceTable=your_table_name
```

### Step 5: Test Migration

Run migration and check logs for:
1. `currentSchema: your_schema_name (default schema for connection)`
2. `INSERT INTO your_schema_name.your_table_name` in SQL logs
3. Successful migration completion

## Troubleshooting

### Issue: "currentSchema not found in logs"

**Possible Causes:**
1. JAR not rebuilt after download
2. Wrong commit checked out
3. Properties file missing schema configuration

**Solution:**
```bash
# 1. Verify commit
git log --oneline -1

# 2. Rebuild JAR
mvn clean package -DskipTests

# 3. Verify properties file has:
grep "yugabyte.schema" your-properties-file.properties
```

### Issue: "SQL shows public.table instead of custom_schema.table"

**Possible Causes:**
1. Schema property not set or empty
2. JAR not rebuilt
3. Code changes not present

**Solution:**
```bash
# 1. Run verification script
./verify_schema_support.sh

# 2. Check properties file
cat your-properties-file.properties | grep schema

# 3. Rebuild JAR
mvn clean package -DskipTests
```

### Issue: "Table not found in schema"

**Possible Causes:**
1. Table doesn't exist in specified schema
2. Schema name typo
3. Wrong database connection

**Solution:**
```bash
# Verify table exists in schema
# Connect to YugabyteDB and run:
SELECT COUNT(*) FROM your_schema.your_table;

# Or let CDM auto-detect (remove schema property temporarily)
```

## Key Files to Check

1. **KnownProperties.java** - Must have `TARGET_YUGABYTE_SCHEMA` constant
2. **YugabyteSession.java** - Must have `currentSchema=` in URL parameters (line ~224)
3. **YugabyteTable.java** - Must have `determineSchemaName()` and `getSchemaName()` methods
4. **YugabyteUpsertStatement.java** - Must use `yugabyteTable.getSchemaName()`

## Verification Commands

```bash
# Check all key code sections exist
grep -q "TARGET_YUGABYTE_SCHEMA" src/main/java/com/datastax/cdm/properties/KnownProperties.java && echo "✅ Property defined" || echo "❌ Missing"
grep -q "currentSchema=" src/main/java/com/datastax/cdm/yugabyte/YugabyteSession.java && echo "✅ currentSchema code present" || echo "❌ Missing"
grep -q "determineSchemaName" src/main/java/com/datastax/cdm/schema/YugabyteTable.java && echo "✅ Schema detection present" || echo "❌ Missing"
grep -q "getSchemaName()" src/main/java/com/datastax/cdm/yugabyte/statement/YugabyteUpsertStatement.java && echo "✅ Schema usage present" || echo "❌ Missing"
```
