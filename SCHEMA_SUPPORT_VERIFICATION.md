# Schema Support Verification Checklist

This document helps verify that all schema support changes are present in your downloaded code.

## ✅ Required Changes in GitHub (Commit: 1edbb4d)

### 1. KnownProperties.java
**File:** `src/main/java/com/datastax/cdm/properties/KnownProperties.java`

**Required Code:**
```java
public static final String TARGET_YUGABYTE_SCHEMA = "spark.cdm.connect.target.yugabyte.schema";

// In static block:
types.put(TARGET_YUGABYTE_SCHEMA, PropertyType.STRING);
defaults.put(TARGET_YUGABYTE_SCHEMA, "public"); // Default PostgreSQL schema
```

**Verification Command:**
```bash
grep -A 2 "TARGET_YUGABYTE_SCHEMA" src/main/java/com/datastax/cdm/properties/KnownProperties.java
```

---

### 2. YugabyteSession.java
**File:** `src/main/java/com/datastax/cdm/yugabyte/YugabyteSession.java`

**Required Code (around line 220-226):**
```java
// 6. Current Schema - set default schema for the connection
String schema = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SCHEMA);
if (schema != null && !schema.trim().isEmpty()) {
    // Use currentSchema parameter (PostgreSQL/YugabyteDB JDBC driver supports this)
    urlParams.add("currentSchema=" + schema.trim());
    logger.info("  currentSchema: {} (default schema for connection)", schema.trim());
}
```

**Verification Command:**
```bash
grep -A 5 "Current Schema" src/main/java/com/datastax/cdm/yugabyte/YugabyteSession.java
```

---

### 3. YugabyteTable.java
**File:** `src/main/java/com/datastax/cdm/schema/YugabyteTable.java`

**Required Code Sections:**

**a) Schema detection logic (around line 57-110):**
```java
String databaseName = connection.getCatalog(); // Get database name from connection

if (tableParts.length == 1) {
    schema = determineSchemaName();
    tableName = tableParts[0];
} else if (tableParts.length == 2) {
    // Could be "schema.table" or "database.table"
    if (databaseName != null && tableParts[0].equalsIgnoreCase(databaseName)) {
        // This is "database.table" format - use configured schema
        schema = determineSchemaName();
        tableName = tableParts[1];
    } else {
        // This is "schema.table" format
        schema = tableParts[0];
        tableName = tableParts[1];
    }
}
```

**b) Safety check (around line 100-110):**
```java
// Ensure schema is never null or empty
if (schema == null || schema.trim().isEmpty()) {
    schema = determineSchemaName(); // Try again, should default to "public"
    if (schema == null || schema.trim().isEmpty()) {
        schema = "public"; // Final fallback
    }
}
this.schemaName = schema.trim();
```

**c) determineSchemaName method (around line 235):**
```java
private String determineSchemaName() {
    String configuredSchema = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SCHEMA);
    if (configuredSchema != null && !configuredSchema.trim().isEmpty()) {
        return configuredSchema.trim();
    }
    return "public"; // Default PostgreSQL schema
}
```

**d) getSchemaName method (around line 228):**
```java
public String getSchemaName() {
    return schemaName;
}
```

**Verification Commands:**
```bash
# Check schema detection
grep -A 10 "databaseName = connection.getCatalog" src/main/java/com/datastax/cdm/schema/YugabyteTable.java

# Check determineSchemaName method
grep -A 8 "private String determineSchemaName" src/main/java/com/datastax/cdm/schema/YugabyteTable.java

# Check getSchemaName method
grep -A 3 "public String getSchemaName" src/main/java/com/datastax/cdm/schema/YugabyteTable.java
```

---

### 4. YugabyteUpsertStatement.java
**File:** `src/main/java/com/datastax/cdm/yugabyte/statement/YugabyteUpsertStatement.java`

**Required Code (around line 117-122):**
```java
private String buildUpsertStatement() {
    StringBuilder sql = new StringBuilder();
    // In YugabyteDB, use schema.table format (e.g., public.table_name or my_schema.table_name)
    // The database is already set in the connection URL
    String schema = yugabyteTable.getSchemaName();
    String tableName = schema + "." + yugabyteTable.getTableName();
```

**Verification Command:**
```bash
grep -A 5 "getSchemaName()" src/main/java/com/datastax/cdm/yugabyte/statement/YugabyteUpsertStatement.java
```

---

## 🔍 Quick Verification Script

Run this script to verify all changes are present:

```bash
#!/bin/bash

echo "=== Verifying Schema Support Changes ==="
echo ""

echo "1. Checking KnownProperties.java..."
if grep -q "TARGET_YUGABYTE_SCHEMA" src/main/java/com/datastax/cdm/properties/KnownProperties.java; then
    echo "   ✅ TARGET_YUGABYTE_SCHEMA property found"
else
    echo "   ❌ TARGET_YUGABYTE_SCHEMA property NOT found"
fi

echo ""
echo "2. Checking YugabyteSession.java..."
if grep -q "currentSchema=" src/main/java/com/datastax/cdm/yugabyte/YugabyteSession.java; then
    echo "   ✅ currentSchema parameter found"
else
    echo "   ❌ currentSchema parameter NOT found"
fi

echo ""
echo "3. Checking YugabyteTable.java..."
if grep -q "determineSchemaName" src/main/java/com/datastax/cdm/schema/YugabyteTable.java; then
    echo "   ✅ determineSchemaName method found"
else
    echo "   ❌ determineSchemaName method NOT found"
fi

if grep -q "getSchemaName()" src/main/java/com/datastax/cdm/schema/YugabyteTable.java; then
    echo "   ✅ getSchemaName method found"
else
    echo "   ❌ getSchemaName method NOT found"
fi

if grep -q "connection.getCatalog()" src/main/java/com/datastax/cdm/schema/YugabyteTable.java; then
    echo "   ✅ database name detection found"
else
    echo "   ❌ database name detection NOT found"
fi

echo ""
echo "4. Checking YugabyteUpsertStatement.java..."
if grep -q "yugabyteTable.getSchemaName()" src/main/java/com/datastax/cdm/yugabyte/statement/YugabyteUpsertStatement.java; then
    echo "   ✅ getSchemaName() usage found"
else
    echo "   ❌ getSchemaName() usage NOT found"
fi

echo ""
echo "=== Verification Complete ==="
```

---

## 📋 Configuration File Example

**File:** `transaction-test-audit.properties`

**Required Configuration:**
```properties
# Database
spark.cdm.connect.target.yugabyte.database=transaction_datastore

# Schema (REQUIRED for custom schemas)
spark.cdm.connect.target.yugabyte.schema=transactions

# Table Configuration
# Origin (Cassandra) - needs keyspace.table format
spark.cdm.schema.origin.keyspaceTable=transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr
# Target (YugabyteDB) - table name only (database and schema defined above)
spark.cdm.schema.target.keyspaceTable=dda_pstd_fincl_txn_cnsmr_by_accntnbr
```

---

## 🧪 Test Verification

After downloading and building, verify the schema support works:

1. **Check the log for currentSchema:**
   ```bash
   grep "currentSchema:" migration.log
   ```
   Should show: `currentSchema: transactions (default schema for connection)`

2. **Check SQL generation:**
   ```bash
   grep "SQL -- yugabyte upsert:" migration.log
   ```
   Should show: `INSERT INTO transactions.table_name` (not `public.table_name`)

3. **Verify data location:**
   ```sql
   SELECT COUNT(*) FROM transactions.your_table;
   ```

---

## 🔗 GitHub Repository

**Repository:** https://github.com/sakthi87/cassandra-data-migrator-yugabyte.git

**Latest Commit with Schema Support:** `1edbb4d`

**To download latest:**
```bash
git clone https://github.com/sakthi87/cassandra-data-migrator-yugabyte.git
cd cassandra-data-migrator-yugabyte
git checkout main
```

**To verify you have the latest:**
```bash
git log --oneline -1
# Should show: 1edbb4d Implement and test custom schema support for YugabyteDB YSQL
```

---

## ⚠️ Common Issues

1. **Issue: currentSchema not in connection URL**
   - **Check:** Verify `YugabyteSession.java` has the currentSchema code (lines 220-226)
   - **Solution:** Rebuild JAR: `mvn clean package -DskipTests`

2. **Issue: Schema defaults to "public" even when configured**
   - **Check:** Verify `YugabyteTable.java` has `determineSchemaName()` method
   - **Check:** Verify property is set: `spark.cdm.connect.target.yugabyte.schema=your_schema`

3. **Issue: SQL shows empty schema (`.table_name`)**
   - **Check:** Verify `YugabyteTable.java` has the safety check for empty schema
   - **Check:** Verify `getSchemaName()` method exists and returns non-empty value

4. **Issue: Table not found errors**
   - **Check:** Verify table exists in the specified schema
   - **Check:** Verify schema name matches exactly (case-sensitive)
   - **Check:** Use auto-detection if unsure (remove schema property, let CDM detect)

---

## ✅ Success Indicators

When schema support is working correctly, you should see:

1. ✅ Log shows: `currentSchema: your_schema (default schema for connection)`
2. ✅ SQL shows: `INSERT INTO your_schema.table_name`
3. ✅ Schema discovery shows: `Discovering schema for table: your_schema.table_name`
4. ✅ Migration completes successfully
5. ✅ Data appears in the correct schema (not in `public`)

