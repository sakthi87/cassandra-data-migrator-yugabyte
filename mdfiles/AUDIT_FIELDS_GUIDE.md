# Audit Fields Population Guide for CDM YugabyteDB Migration

## Overview

When migrating data from Cassandra to YugabyteDB, your target table may have additional **audit fields** that don't exist in the source table. CDM provides the **Constant Columns Feature** to automatically populate these fields with predefined values during migration.

---

## Common Audit Field Use Cases

| Audit Field | Purpose | Example Value |
|-------------|---------|---------------|
| `created_by` | User/process that created the record | `'CDM_MIGRATION'` |
| `migration_date` | When the migration occurred | `'2024-12-16'` |
| `source_system` | Origin database identifier | `'CASSANDRA_PROD'` |
| `migration_run_id` | Unique migration batch ID | `12345` |
| `data_version` | Version of migrated data | `1` |
| `is_migrated` | Flag indicating migrated data | `true` |
| `migrated_timestamp` | Timestamp of migration | `'2024-12-16T10:30:00Z'` |

---

## Configuration

### Basic Syntax

Add the following properties to your CDM properties file:

```properties
# Constant Columns Feature - Audit Fields
spark.cdm.feature.constantColumns.names=<column1>,<column2>,<column3>
spark.cdm.feature.constantColumns.values=<value1>,<value2>,<value3>
spark.cdm.feature.constantColumns.splitRegex=,
```

### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `.names` | Comma-separated list of target column names | Yes |
| `.values` | Comma-separated list of values (CQLSH syntax) | Yes |
| `.splitRegex` | Delimiter for splitting values (default: `,`) | No |

---

## Examples

### Example 1: Basic Audit Fields

**Scenario:** Add `created_by`, `migration_date`, and `source_system` to migrated records.

**Target Table (YugabyteDB):**
```sql
CREATE TABLE my_keyspace.my_table (
    id UUID PRIMARY KEY,
    name TEXT,
    email TEXT,
    -- Audit fields (not in source)
    created_by TEXT,
    migration_date DATE,
    source_system TEXT
);
```

**CDM Configuration:**
```properties
# Audit fields configuration
spark.cdm.feature.constantColumns.names=created_by,migration_date,source_system
spark.cdm.feature.constantColumns.values='CDM_MIGRATION','2024-12-16','CASSANDRA_PROD'
```

**Result:** Every migrated record will have:
- `created_by = 'CDM_MIGRATION'`
- `migration_date = '2024-12-16'`
- `source_system = 'CASSANDRA_PROD'`

---

### Example 2: Numeric and Boolean Audit Fields

**Scenario:** Add `migration_run_id`, `data_version`, and `is_migrated` fields.

**Target Table (YugabyteDB):**
```sql
CREATE TABLE my_keyspace.my_table (
    id UUID PRIMARY KEY,
    data TEXT,
    -- Audit fields
    migration_run_id BIGINT,
    data_version INT,
    is_migrated BOOLEAN
);
```

**CDM Configuration:**
```properties
spark.cdm.feature.constantColumns.names=migration_run_id,data_version,is_migrated
spark.cdm.feature.constantColumns.values=1702732800000,1,true
```

**Result:**
- `migration_run_id = 1702732800000` (epoch timestamp)
- `data_version = 1`
- `is_migrated = true`

---

### Example 3: Timestamp Audit Fields

**Scenario:** Add timestamp-based audit fields.

**Target Table (YugabyteDB):**
```sql
CREATE TABLE my_keyspace.my_table (
    id UUID PRIMARY KEY,
    content TEXT,
    -- Audit fields
    migrated_at TIMESTAMP,
    migration_batch TEXT
);
```

**CDM Configuration:**
```properties
spark.cdm.feature.constantColumns.names=migrated_at,migration_batch
spark.cdm.feature.constantColumns.values='2024-12-16T10:30:00.000Z','BATCH_001'
```

---

### Example 4: Complex Values with Custom Delimiter

**Scenario:** Values contain commas (lists, maps, etc.)

**Target Table (YugabyteDB):**
```sql
CREATE TABLE my_keyspace.my_table (
    id UUID PRIMARY KEY,
    name TEXT,
    -- Audit fields with complex types
    migration_tags LIST<TEXT>,
    migration_metadata MAP<TEXT, TEXT>
);
```

**CDM Configuration:**
```properties
# Use pipe (|) as delimiter since values contain commas
spark.cdm.feature.constantColumns.names=migration_tags|migration_metadata
spark.cdm.feature.constantColumns.values=['source_cassandra','env_prod']|{'migrator':'CDM','version':'4.0'}
spark.cdm.feature.constantColumns.splitRegex=\\|
```

**Note:** When using regex special characters like `|`, escape them with `\\`.

---

## Value Format Reference

Use CQLSH-compatible syntax for values:

| Data Type | Format | Example |
|-----------|--------|---------|
| TEXT/VARCHAR | Single quotes | `'my_value'` |
| INT/BIGINT | No quotes | `12345` |
| BOOLEAN | No quotes | `true` or `false` |
| TIMESTAMP | ISO format in quotes | `'2024-12-16T10:30:00Z'` |
| DATE | Date string in quotes | `'2024-12-16'` |
| UUID | UUID string in quotes | `'550e8400-e29b-41d4-a716-446655440000'` |
| LIST | Brackets with quoted elements | `['val1','val2']` |
| SET | Braces with quoted elements | `{'val1','val2'}` |
| MAP | Braces with key:value pairs | `{'key1':'val1'}` |

---

## Complete Configuration Example

Here's a complete `yugabyte-ysql-migration.properties` snippet with audit fields:

```properties
# =============================================================================
# SCHEMA CONFIGURATION
# =============================================================================
spark.cdm.schema.origin.keyspaceTable=source_keyspace.source_table
spark.cdm.schema.target.keyspaceTable=target_keyspace.target_table

# =============================================================================
# AUDIT FIELDS CONFIGURATION (Constant Columns Feature)
# =============================================================================
# These fields will be automatically populated in the target table
# during migration with the specified constant values.
#
# IMPORTANT:
# - Column names must exist in the TARGET table schema
# - Column names should NOT exist in the SOURCE table
# - Values must use CQLSH-compatible syntax
# - Number of names must match number of values

# Audit field names (must exist in target table)
spark.cdm.feature.constantColumns.names=created_by,migration_date,source_system,migration_run_id,is_migrated

# Audit field values (CQLSH syntax)
spark.cdm.feature.constantColumns.values='CDM_MIGRATION','2024-12-16','CASSANDRA_PROD',1702732800000,true

# Value delimiter (default is comma, change if values contain commas)
spark.cdm.feature.constantColumns.splitRegex=,

# =============================================================================
# CONNECTION SETTINGS
# =============================================================================
# ... rest of configuration ...
```

---

## Best Practices

### 1. Column Naming Conventions

Use clear, consistent naming for audit fields:

```
created_by          → Who/what created the record
created_at          → When the record was created
migrated_by         → Migration process identifier
migrated_at         → Migration timestamp
migration_run_id    → Unique batch/run identifier
source_system       → Source database name
source_table        → Source table name
data_version        → Version number for the data
is_migrated         → Boolean flag for migrated records
```

### 2. Use Epoch Timestamps for Run IDs

Generate unique run IDs based on epoch time:

```bash
# Get current epoch milliseconds
date +%s000

# Example output: 1702732800000
```

```properties
spark.cdm.feature.constantColumns.names=migration_run_id
spark.cdm.feature.constantColumns.values=1702732800000
```

### 3. Include Environment Information

Track which environment data came from:

```properties
spark.cdm.feature.constantColumns.names=source_env,target_env
spark.cdm.feature.constantColumns.values='PROD_CASSANDRA','PROD_YUGABYTE'
```

### 4. Version Your Migrations

Track migration versions for data lineage:

```properties
spark.cdm.feature.constantColumns.names=migration_version,schema_version
spark.cdm.feature.constantColumns.values='v2.0.0','schema_v3'
```

---

## Troubleshooting

### Error: "Constant column X is not found on the target table"

**Cause:** The column name specified doesn't exist in the target table.

**Solution:** 
1. Verify the column exists in the target YugabyteDB table
2. Check for typos in column names
3. Ensure column names match case (YugabyteDB is case-sensitive for quoted identifiers)

### Error: "Constant column names and values are of different sizes"

**Cause:** Number of column names doesn't match number of values.

**Solution:**
1. Count the items in `.names` and `.values`
2. Ensure they have the same number of elements
3. Check the `.splitRegex` is correctly splitting values

### Error: "Constant column value cannot be parsed as type X"

**Cause:** Value format doesn't match the column data type.

**Solution:**
1. Use correct CQLSH syntax for the data type
2. Check quotes for string types
3. Verify timestamp/date formats

### Complex Values Not Parsing Correctly

**Cause:** Default comma delimiter conflicts with values containing commas.

**Solution:**
```properties
# Use a different delimiter
spark.cdm.feature.constantColumns.splitRegex=\\|

# Separate values with pipe
spark.cdm.feature.constantColumns.values='value1'|'value2'|['list','items']
```

---

## YugabyteDB-Specific Considerations

### 1. Primary Key Constraints

Audit columns **cannot** be part of the primary key unless you also define them with constant values:

```sql
-- This works (audit field is NOT in primary key)
CREATE TABLE t (
    id UUID PRIMARY KEY,
    data TEXT,
    created_by TEXT  -- Audit field
);

-- This requires careful handling (audit field IS in primary key)
CREATE TABLE t (
    id UUID,
    tenant_id TEXT,  -- This must have a constant value if it's an audit field
    data TEXT,
    PRIMARY KEY (id, tenant_id)
);
```

### 2. Default Values vs Constant Columns

You can also use YugabyteDB default values instead of CDM constant columns:

```sql
-- YugabyteDB table with defaults
CREATE TABLE my_table (
    id UUID PRIMARY KEY,
    data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_migrated BOOLEAN DEFAULT true
);
```

**However**, CDM Constant Columns is preferred when:
- You need specific values (not defaults)
- You want to track migration batches with unique IDs
- The source system name varies per migration

### 3. Index Considerations

If you create indexes on audit fields, ensure the constant values are appropriate:

```sql
-- Index on is_migrated (useful for filtering migrated vs non-migrated)
CREATE INDEX idx_migrated ON my_table(is_migrated);
```

---

## Summary

| Feature | Description |
|---------|-------------|
| **Purpose** | Populate extra columns in target table during migration |
| **Configuration** | `spark.cdm.feature.constantColumns.*` properties |
| **Value Format** | CQLSH-compatible syntax |
| **Use Cases** | Audit fields, migration tracking, data lineage |
| **Limitations** | Target columns must exist, values are constant (same for all records) |

The Constant Columns feature is a powerful way to add audit and tracking information to your migrated data without modifying the source data or writing custom transformation code.

