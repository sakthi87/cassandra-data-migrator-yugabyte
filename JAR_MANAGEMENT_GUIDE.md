# JAR Management Guide for Cassandra Data Migrator with YugabyteDB Support

## 🎉 Build Success!

Your modified Cassandra Data Migrator with YugabyteDB support has been successfully built! Here's what you have:

### Generated JAR Files

1. **`cassandra-data-migrator-5.5.2-SNAPSHOT.jar`** (37MB) - **Main JAR with all dependencies**
   - This is the **shaded/fat JAR** that includes all dependencies
   - **Use this one for running your migrations**
   - Contains PostgreSQL JDBC driver for YugabyteDB support

2. **`original-cassandra-data-migrator-5.5.2-SNAPSHOT.jar`** (280KB) - Original JAR without dependencies
   - This is the original JAR without dependencies
   - **Don't use this one** - it's missing required libraries

## 📋 Do You Have to Run Maven Every Time?

### **Short Answer: NO!** 

Here are your options for efficient JAR management:

### **Option 1: Use the Existing JAR (Recommended)**
```bash
# Your JAR is ready to use right now!
java -cp target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  com.datastax.cdm.job.YugabyteMigrate \
  --config-file yugabyte-migrate.properties
```

### **Option 2: Copy JAR to a Permanent Location**
```bash
# Create a dedicated directory for your migration tools
mkdir -p ~/migration-tools
cp target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar ~/migration-tools/cdm-yugabyte.jar

# Now you can use it from anywhere
java -cp ~/migration-tools/cdm-yugabyte.jar \
  com.datastax.cdm.job.YugabyteMigrate \
  --config-file yugabyte-migrate.properties
```

### **Option 3: Create a Shell Script for Easy Execution**
```bash
# Create a script
cat > ~/migration-tools/run-yugabyte-migration.sh << 'EOF'
#!/bin/bash
JAR_PATH="$HOME/migration-tools/cdm-yugabyte.jar"
CONFIG_FILE="${1:-yugabyte-migrate.properties}"

if [ ! -f "$JAR_PATH" ]; then
    echo "JAR file not found at $JAR_PATH"
    echo "Please copy the JAR file first:"
    echo "cp target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar ~/migration-tools/cdm-yugabyte.jar"
    exit 1
fi

java -cp "$JAR_PATH" com.datastax.cdm.job.YugabyteMigrate --config-file "$CONFIG_FILE"
EOF

chmod +x ~/migration-tools/run-yugabyte-migration.sh

# Usage
~/migration-tools/run-yugabyte-migration.sh yugabyte-migrate.properties
```

## 🔄 When Do You Need to Rebuild?

You only need to run `mvn clean package` when you:

1. **Modify Java/Scala source code** (like we just did)
2. **Add new dependencies** to `pom.xml`
3. **Change configuration properties**
4. **Update CDM version**

### **For Configuration Changes Only:**
If you only change `.properties` files, you **don't need to rebuild**:
```bash
# Just edit your properties file and run
java -cp target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  com.datastax.cdm.job.YugabyteMigrate \
  --config-file your-updated-config.properties
```

## 🚀 Quick Start Commands

### **1. Test Your Build**
```bash
# Verify the JAR contains your YugabyteDB classes
jar -tf target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar | grep -i yugabyte
```

### **2. Run a Test Migration**
```bash
# Update your configuration file first
nano yugabyte-migrate.properties

# Then run the migration
java -cp target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  com.datastax.cdm.job.YugabyteMigrate \
  --config-file yugabyte-migrate.properties
```

### **3. Check JAR Contents**
```bash
# See what's inside your JAR
jar -tf target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar | head -20

# Check if PostgreSQL driver is included
jar -tf target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar | grep postgresql
```

## 📁 Recommended Project Structure

```
~/migration-tools/
├── cdm-yugabyte.jar                    # Your migration JAR
├── run-yugabyte-migration.sh           # Execution script
├── configs/
│   ├── yugabyte-migrate.properties     # Main config
│   ├── customer-migration.properties   # Customer table config
│   └── test-config.properties          # Test environment config
└── logs/                               # Migration logs
```

## 🔧 Development Workflow

### **For Active Development:**
```bash
# Make code changes
# Then rebuild
mvn clean package -DskipTests

# Test immediately
java -cp target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  com.datastax.cdm.job.YugabyteMigrate \
  --config-file yugabyte-migrate.properties
```

### **For Production Use:**
```bash
# Build once
mvn clean package -DskipTests

# Copy to production location
cp target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar /opt/migration-tools/

# Use from production location
java -cp /opt/migration-tools/cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  com.datastax.cdm.job.YugabyteMigrate \
  --config-file /opt/migration-tools/configs/production.properties
```

## ⚡ Performance Tips

1. **Keep the JAR in memory** - The JAR is only 37MB, so it loads quickly
2. **Use SSD storage** - Faster JAR loading
3. **Pre-warm JVM** - Run a small test first to warm up the JVM
4. **Monitor memory** - Your 13M row migration will need adequate heap space

## 🎯 Next Steps

1. **Update your configuration** with real connection details
2. **Test with a small dataset** first
3. **Create your target table** in YugabyteDB YSQL
4. **Run the migration** for your customer table
5. **Monitor performance** and adjust as needed

Your JAR is ready to migrate your 13 million customer records from DataStax Cassandra to YugabyteDB YSQL! 🚀
