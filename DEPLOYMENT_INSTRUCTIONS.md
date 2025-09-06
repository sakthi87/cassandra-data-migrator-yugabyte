# YugabyteDB Migration Deployment Instructions

## Files to Transfer to Target Machine

### Required Files:
1. **JAR File**: `target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar` (35MB)
2. **Properties File**: Choose one of:
   - `my-migration.properties` (basic template)
   - `example-migration.properties` (optimized for 13M rows)
   - `yugabyte-migrate.properties` (detailed template)

### Optional Files:
- `YUGABYTE_MIGRATION_README.md` (documentation)
- `FINAL_MIGRATION_COMMAND.md` (command reference)

## Target Machine Requirements

### Prerequisites:
1. **Apache Spark** (version 3.x recommended)
2. **Java 11 or 17** (you have Java 17, which is perfect)
3. **Network access** to both:
   - DataStax Cassandra cluster
   - YugabyteDB YSQL cluster

### Spark Installation Options:

#### Option 1: Using Package Manager
```bash
# On Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk
wget https://archive.apache.org/dist/spark/spark-3.5.0/spark-3.5.0-bin-hadoop3.tgz
tar -xzf spark-3.5.0-bin-hadoop3.tgz
sudo mv spark-3.5.0-bin-hadoop3 /opt/spark
echo 'export PATH=$PATH:/opt/spark/bin' >> ~/.bashrc
source ~/.bashrc
```

#### Option 2: Using Conda
```bash
conda install -c conda-forge openjdk=17
conda install -c conda-forge apache-spark
```

## Directory Structure on Target Machine

Create this structure on your target machine:
```
/opt/migration/
├── cassandra-data-migrator-5.5.2-SNAPSHOT.jar
├── my-migration.properties
└── logs/
```

## Configuration Steps

### 1. Update Properties File
Edit your chosen properties file with actual values:

```properties
# SOURCE: DataStax Cassandra
spark.cdm.connect.origin.host=your_actual_cassandra_host
spark.cdm.connect.origin.port=9042
spark.cdm.connect.origin.username=your_username
spark.cdm.connect.origin.password=your_password
spark.cdm.connect.origin.localDC=your_datacenter

# TARGET: YugabyteDB YSQL
spark.cdm.connect.target.yugabyte.host=your_actual_yugabyte_host
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=your_database
spark.cdm.connect.target.yugabyte.username=your_username
spark.cdm.connect.target.yugabyte.password=your_password

# TABLE NAMES
spark.cdm.schema.origin.keyspaceTable=your_keyspace.your_table
spark.cdm.schema.target.keyspaceTable=your_keyspace.your_table
```

### 2. Test Connectivity
```bash
# Test Cassandra connection
cqlsh your_cassandra_host 9042 -u your_username -p your_password

# Test YugabyteDB connection
psql -h your_yugabyte_host -p 5433 -U your_username -d your_database
```

## Running the Migration

### From Directory: `/opt/migration/`

```bash
spark-submit \
  --properties-file my-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.your_table" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  &> migration_log_$(date +%Y%m%d_%H%M%S).txt
```

### For Large Tables (13M+ rows):
```bash
spark-submit \
  --properties-file example-migration.properties \
  --conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.your_table" \
  --master "local[*]" \
  --driver-memory 25G \
  --executor-memory 25G \
  --class com.datastax.cdm.job.YugabyteMigrate \
  cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
  &> large_table_migration_$(date +%Y%m%d_%H%M%S).txt
```

## Monitoring

### Check Progress:
```bash
# Monitor the log file
tail -f migration_log_*.txt

# Check Spark UI (if running in cluster mode)
# Access: http://your_machine:4040
```

### Expected Output:
- Migration progress indicators
- Row count statistics
- Performance metrics
- Completion status

## Troubleshooting

### Common Issues:
1. **Connection refused**: Check host/port/credentials
2. **Out of memory**: Increase driver/executor memory
3. **Table not found**: Verify keyspace.table names
4. **Permission denied**: Check database user permissions

### Logs Location:
- Application logs: `migration_log_*.txt`
- Spark logs: `/opt/spark/logs/` (if using local Spark installation)
