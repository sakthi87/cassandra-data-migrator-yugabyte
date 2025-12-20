#!/bin/bash
# Optimized migration script for local Mac (8GB RAM, 8 cores)
# Based on system capacity analysis

echo "=========================================="
echo "Optimized Migration for Local Machine"
echo "=========================================="
echo ""
echo "System Capacity:"
echo "  - Total Memory: 8 GB"
echo "  - Total Cores: 8"
echo "  - Available for Spark: ~3 GB, 6 cores"
echo ""

# Configuration selection
CONFIG=${1:-balanced}  # conservative, balanced, or aggressive

case $CONFIG in
  conservative)
    echo "Using CONSERVATIVE configuration (stability focus)"
    SPARK_CORES=6
    DRIVER_MEMORY=2G
    PARALLELISM=12
    PARTITIONS=12
    BATCH_SIZE=50
    FETCH_SIZE=2000
    ;;
  balanced)
    echo "Using BALANCED configuration (recommended)"
    SPARK_CORES=6
    DRIVER_MEMORY=3G
    PARALLELISM=18
    PARTITIONS=18
    BATCH_SIZE=75
    FETCH_SIZE=3000
    ;;
  aggressive)
    echo "Using AGGRESSIVE configuration (max throughput)"
    SPARK_CORES=6
    DRIVER_MEMORY=3G
    PARALLELISM=24
    PARTITIONS=24
    BATCH_SIZE=100
    FETCH_SIZE=5000
    ;;
  *)
    echo "Invalid config: $CONFIG (use: conservative, balanced, aggressive)"
    exit 1
    ;;
esac

echo ""
echo "Configuration:"
echo "  - Spark Cores: $SPARK_CORES"
echo "  - Driver Memory: $DRIVER_MEMORY"
echo "  - Parallelism: $PARALLELISM"
echo "  - Partitions: $PARTITIONS"
echo "  - Batch Size: $BATCH_SIZE"
echo "  - Fetch Size: $FETCH_SIZE"
echo ""

# Update properties file temporarily
PROP_FILE="transaction-test-audit.properties"
BACKUP_FILE="${PROP_FILE}.backup.$(date +%Y%m%d_%H%M%S)"

# Backup original
cp "$PROP_FILE" "$BACKUP_FILE"

# Update properties (macOS compatible)
sed -i '' "s/spark.cdm.perfops.numParts=.*/spark.cdm.perfops.numParts=$PARTITIONS/" "$PROP_FILE"
sed -i '' "s/spark.cdm.connect.target.yugabyte.batchSize=.*/spark.cdm.connect.target.yugabyte.batchSize=$BATCH_SIZE/" "$PROP_FILE"
sed -i '' "s/spark.cdm.perfops.fetchSizeInRows=.*/spark.cdm.perfops.fetchSizeInRows=$FETCH_SIZE/" "$PROP_FILE"

echo "Updated properties file (backup: $BACKUP_FILE)"
echo ""

# Find JAR file
JAR_FILE=$(find target -name "cassandra-data-migrator-*.jar" | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "❌ JAR file not found. Run 'mvn package' first."
    exit 1
fi

echo "Starting migration..."
echo ""

# Run migration
spark-submit \
    --master local[$SPARK_CORES] \
    --driver-memory $DRIVER_MEMORY \
    --conf spark.default.parallelism=$PARALLELISM \
    --conf spark.sql.shuffle.partitions=$PARALLELISM \
    --conf spark.executor.memoryOverhead=1G \
    --conf spark.memory.fraction=0.7 \
    --conf spark.memory.storageFraction=0.2 \
    --conf spark.network.timeout=600s \
    --conf spark.executor.heartbeatInterval=60s \
    --conf spark.locality.wait=0s \
    --conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
    --conf spark.sql.execution.arrow.pyspark.enabled=false \
    --properties-file "$PROP_FILE" \
    --class com.datastax.cdm.job.YugabyteMigrate \
    "$JAR_FILE" 2>&1 | tee migration_optimized_${CONFIG}_$(date +%Y%m%d_%H%M%S).log

EXIT_CODE=$?

# Restore original properties
if [ -f "$BACKUP_FILE" ]; then
    mv "$BACKUP_FILE" "$PROP_FILE"
fi

echo ""
echo "=========================================="
echo "Migration Completed (Exit Code: $EXIT_CODE)"
echo "=========================================="

exit $EXIT_CODE
