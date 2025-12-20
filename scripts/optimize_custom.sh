#!/bin/bash
# Custom optimization script - fine-tuned for maximum throughput

PARTITIONS=${1:-30}  # Default 30, can override
BATCH_SIZE=${2:-75}   # Default 75, can override
FETCH_SIZE=${3:-3000} # Default 3000, can override

echo "=========================================="
echo "Custom Optimized Migration"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  - Spark: local[6]"
echo "  - Driver Memory: 3G"
echo "  - Parallelism: $PARTITIONS"
echo "  - Partitions: $PARTITIONS"
echo "  - Batch Size: $BATCH_SIZE"
echo "  - Fetch Size: $FETCH_SIZE"
echo ""

# Backup and update properties
PROP_FILE="transaction-test-audit.properties"
BACKUP_FILE="${PROP_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
cp "$PROP_FILE" "$BACKUP_FILE"

sed -i '' "s/spark.cdm.perfops.numParts=.*/spark.cdm.perfops.numParts=$PARTITIONS/" "$PROP_FILE"
sed -i '' "s/spark.cdm.connect.target.yugabyte.batchSize=.*/spark.cdm.connect.target.yugabyte.batchSize=$BATCH_SIZE/" "$PROP_FILE"
sed -i '' "s/spark.cdm.perfops.fetchSizeInRows=.*/spark.cdm.perfops.fetchSizeInRows=$FETCH_SIZE/" "$PROP_FILE"

# Find JAR
JAR_FILE=$(find target -name "cassandra-data-migrator-*.jar" | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "❌ JAR file not found. Run 'mvn package' first."
    exit 1
fi

# Run migration
spark-submit \
    --master local[6] \
    --driver-memory 3G \
    --conf spark.default.parallelism=$PARTITIONS \
    --conf spark.sql.shuffle.partitions=$PARTITIONS \
    --conf spark.executor.memoryOverhead=1G \
    --conf spark.memory.fraction=0.7 \
    --conf spark.memory.storageFraction=0.2 \
    --conf spark.network.timeout=600s \
    --conf spark.executor.heartbeatInterval=60s \
    --conf spark.locality.wait=0s \
    --conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
    --properties-file "$PROP_FILE" \
    --class com.datastax.cdm.job.YugabyteMigrate \
    "$JAR_FILE" 2>&1 | tee migration_custom_${PARTITIONS}parts_$(date +%Y%m%d_%H%M%S).log

EXIT_CODE=$?

# Restore properties
if [ -f "$BACKUP_FILE" ]; then
    mv "$BACKUP_FILE" "$PROP_FILE"
fi

exit $EXIT_CODE
