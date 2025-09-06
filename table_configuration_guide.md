# Table Configuration Guide for YugabyteDB Migration

## 🎯 **Where to Specify Source and Target Table Names**

### **1. In the Properties File (`yugabyte-migrate.properties`)**

```properties
# ==========================================================================
# Schema Configuration - THIS IS WHERE YOU SPECIFY TABLE NAMES
# ==========================================================================

# SOURCE: Your Cassandra table (keyspace.table format)
spark.cdm.schema.origin.keyspaceTable=your_keyspace.customer

# TARGET: Your YugabyteDB YSQL table (keyspace.table format)  
spark.cdm.schema.target.keyspaceTable=your_keyspace.customer
```

### **2. In the Spark Submit Command (Alternative)**

```bash
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --conf spark.cdm.schema.target.keyspaceTable="your_keyspace.customer" \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## 📋 **Complete Configuration Example**

### **Step 1: Update `yugabyte-migrate.properties`**

```properties
# ==========================================================================
# Origin (DataStax Cassandra) Connection
# ==========================================================================
spark.cdm.connect.origin.host=your-cassandra-host.com
spark.cdm.connect.origin.port=9042
spark.cdm.connect.origin.username=cassandra
spark.cdm.connect.origin.password=your-cassandra-password

# ==========================================================================
# Target (YugabyteDB YSQL) Connection
# ==========================================================================
spark.cdm.connect.target.type=yugabyte
spark.cdm.connect.target.yugabyte.host=your-yugabyte-host.com
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=yugabyte
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=your-yugabyte-password

# ==========================================================================
# Schema Configuration - TABLE NAMES HERE
# ==========================================================================
spark.cdm.schema.origin.keyspaceTable=my_keyspace.customer
spark.cdm.schema.target.keyspaceTable=my_keyspace.customer

# ==========================================================================
# Performance Configuration
# ==========================================================================
spark.cdm.perfops.numParts=64
spark.cdm.perfops.fetchSize=5000
spark.cdm.perfops.batchSize=100
spark.cdm.perfops.rateLimit.origin=1000
spark.cdm.perfops.rateLimit.target=1000

# ==========================================================================
# Run Tracking (Enable for incremental updates)
# ==========================================================================
spark.cdm.track.run=true
spark.cdm.run.id=0
spark.cdm.prev.run.id=0
```

### **Step 2: Run the Migration**

```bash
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  &> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

## 🔧 **Table Name Configuration Options**

### **Option 1: Same Table Name (Recommended)**
```properties
# Both source and target use the same table name
spark.cdm.schema.origin.keyspaceTable=my_keyspace.customer
spark.cdm.schema.target.keyspaceTable=my_keyspace.customer
```

### **Option 2: Different Table Names**
```properties
# Source and target have different names
spark.cdm.schema.origin.keyspaceTable=my_keyspace.customer
spark.cdm.schema.target.keyspaceTable=my_keyspace.customer_migrated
```

### **Option 3: Different Keyspaces**
```properties
# Source and target in different keyspaces
spark.cdm.schema.origin.keyspaceTable=old_keyspace.customer
spark.cdm.schema.target.keyspaceTable=new_keyspace.customer
```

## 📊 **Table Name Format Requirements**

### **Cassandra Table Name:**
- **Format**: `keyspace.table_name`
- **Example**: `my_keyspace.customer`
- **Requirements**: 
  - Keyspace must exist in Cassandra
  - Table must exist in Cassandra
  - Use exact case-sensitive names

### **YugabyteDB YSQL Table Name:**
- **Format**: `schema.table_name` (PostgreSQL format)
- **Example**: `my_keyspace.customer`
- **Requirements**:
  - Schema must exist in YugabyteDB
  - Table will be created automatically if it doesn't exist
  - Use exact case-sensitive names

## 🚀 **Quick Start Example**

### **For Your 13M Row Customer Table:**

1. **Update the properties file:**
```properties
spark.cdm.schema.origin.keyspaceTable=your_keyspace.customer
spark.cdm.schema.target.keyspaceTable=your_keyspace.customer
```

2. **Run the migration:**
```bash
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## 🔍 **Verification Steps**

### **1. Check Source Table Exists:**
```sql
-- Connect to Cassandra
cqlsh your-cassandra-host.com
USE your_keyspace;
DESCRIBE TABLE customer;
```

### **2. Check Target Table (After Migration):**
```sql
-- Connect to YugabyteDB YSQL
psql -h your-yugabyte-host.com -p 5433 -U yugabyte -d yugabyte
\c your_keyspace
\d customer
```

### **3. Verify Row Counts:**
```sql
-- Cassandra
SELECT COUNT(*) FROM your_keyspace.customer;

-- YugabyteDB YSQL
SELECT COUNT(*) FROM your_keyspace.customer;
```

## ⚠️ **Important Notes**

1. **Table Creation**: YugabyteDB table will be created automatically if it doesn't exist
2. **Schema Mapping**: Data types are automatically mapped from Cassandra to PostgreSQL
3. **Primary Keys**: Primary key structure is preserved
4. **Indexes**: Secondary indexes are not migrated (create manually if needed)
5. **Case Sensitivity**: Table names are case-sensitive

## 🎯 **Your Specific Configuration**

For your customer table with 13M rows, use:

```properties
spark.cdm.schema.origin.keyspaceTable=your_keyspace.customer
spark.cdm.schema.target.keyspaceTable=your_keyspace.customer
```

Replace `your_keyspace` with your actual keyspace name and `customer` with your actual table name.
