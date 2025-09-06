# JAR File Usage Guide for YugabyteDB Migration

## 🎯 **Best Practice: Download Entire Repository (Recommended)**

### **✅ Why Download the Full Repository:**

1. **Complete Documentation**: All guides, examples, and troubleshooting docs
2. **Example Configurations**: Pre-configured property files
3. **Scripts and Tools**: Token range analysis, monitoring scripts
4. **Version Control**: Track changes and updates
5. **Troubleshooting**: Access to source code for debugging
6. **Future Updates**: Easy to pull latest changes

### **📁 What You Get with Full Repository:**
```
cassandra-data-migrator-yugabyte/
├── target/
│   └── cassandra-data-migrator-5.5.2-SNAPSHOT.jar  # Ready-to-use JAR
├── yugabyte-migrate.properties                      # Configuration template
├── example-migration.properties                     # Example configuration
├── YUGABYTE_MIGRATION_README.md                     # Complete setup guide
├── RUN_YUGABYTE_MIGRATION.md                        # Migration commands
├── incremental_migration_guide.md                   # Incremental updates
├── table_configuration_guide.md                     # Table configuration
├── get_token_ranges.cql                             # Token analysis queries
├── analyze_token_ranges.py                          # Python analysis script
└── multi_node_analysis.md                           # Multi-node guidance
```

## 🔧 **Option 1: Full Repository (Recommended)**

### **Step 1: Clone the Repository**
```bash
git clone https://github.com/sakthi87/cassandra-data-migrator-yugabyte.git
cd cassandra-data-migrator-yugabyte
```

### **Step 2: Use the JAR and Configuration**
```bash
# Use the built JAR with example configuration
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

## 📦 **Option 2: JAR Only (Minimal Setup)**

### **Step 1: Download Only the JAR**
```bash
# Download from GitHub release
curl -L -o cassandra-data-migrator-yugabyte.jar \
  https://github.com/sakthi87/cassandra-data-migrator-yugabyte/releases/download/v1.0.0/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

### **Step 2: Create Properties File Manually**
```bash
# Create your configuration file
cat > my-migration.properties << 'EOF'
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
# Schema Configuration
# ==========================================================================
spark.cdm.schema.origin.keyspaceTable=your_keyspace.customer
spark.cdm.schema.target.keyspaceTable=your_keyspace.customer

# ==========================================================================
# Performance Configuration
# ==========================================================================
spark.cdm.perfops.numParts=64
spark.cdm.perfops.fetchSize=5000
spark.cdm.perfops.batchSize=100
spark.cdm.perfops.rateLimit.origin=1000
spark.cdm.perfops.rateLimit.target=1000

# ==========================================================================
# Run Tracking
# ==========================================================================
spark.cdm.track.run=true
spark.cdm.run.id=0
spark.cdm.prev.run.id=0
EOF
```

### **Step 3: Run Migration**
```bash
spark-submit \
  --properties-file my-migration.properties \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-yugabyte.jar
```

## 📊 **Comparison: Full Repo vs JAR Only**

| Aspect | Full Repository | JAR Only |
|--------|----------------|----------|
| **Setup Time** | ✅ 5 minutes | ❌ 30+ minutes |
| **Documentation** | ✅ Complete guides | ❌ Manual research |
| **Configuration** | ✅ Pre-built templates | ❌ Create from scratch |
| **Troubleshooting** | ✅ All tools available | ❌ Limited debugging |
| **Updates** | ✅ Easy git pull | ❌ Manual download |
| **File Size** | ❌ ~50MB | ✅ ~37MB |
| **Dependencies** | ✅ All included | ✅ All included |

## 🚀 **Recommended Workflow**

### **For Production Use:**
```bash
# 1. Clone the repository
git clone https://github.com/sakthi87/cassandra-data-migrator-yugabyte.git
cd cassandra-data-migrator-yugabyte

# 2. Copy and customize configuration
cp yugabyte-migrate.properties my-customer-migration.properties
# Edit my-customer-migration.properties with your actual values

# 3. Run migration
spark-submit \
  --properties-file my-customer-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  &> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

### **For Quick Testing:**
```bash
# 1. Download JAR only
curl -L -o cdm.jar \
  https://github.com/sakthi87/cassandra-data-migrator-yugabyte/releases/download/v1.0.0/cassandra-data-migrator-5.5.2-SNAPSHOT.jar

# 2. Create minimal config
cat > test.properties << 'EOF'
spark.cdm.connect.origin.host=localhost
spark.cdm.connect.origin.port=9042
spark.cdm.connect.origin.username=cassandra
spark.cdm.connect.origin.password=cassandra
spark.cdm.connect.target.type=yugabyte
spark.cdm.connect.target.yugabyte.host=localhost
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=yugabyte
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=yugabyte
spark.cdm.schema.origin.keyspaceTable=test.customer
spark.cdm.schema.target.keyspaceTable=test.customer
EOF

# 3. Run test
spark-submit \
  --properties-file test.properties \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cdm.jar
```

## 🎯 **My Recommendation for Your 13M Row Migration**

### **Use Full Repository Because:**

1. **You'll need the documentation** for troubleshooting
2. **Token range analysis tools** will help optimize performance
3. **Incremental update guides** for handling failures
4. **Multiple configuration examples** for different scenarios
5. **Easy updates** if you need to modify the code

### **Quick Start Command:**
```bash
# Clone and run
git clone https://github.com/sakthi87/cassandra-data-migrator-yugabyte.git
cd cassandra-data-migrator-yugabyte

# Update the properties file with your actual values
# Then run:
spark-submit \
  --properties-file yugabyte-migrate.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar
```

**The full repository gives you everything you need for a successful migration!** 🚀
