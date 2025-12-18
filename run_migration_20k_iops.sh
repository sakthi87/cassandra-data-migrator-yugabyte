#!/bin/bash
# Spark Submit Command for 20K IOPS Target
# Optimized for 12-core, 64GB RAM machine with cross-datacenter network

# Configuration
JAR_FILE="cassandra-data-migrator-5.5.2-SNAPSHOT.jar"
PROPERTIES_FILE="transaction-test-audit.properties"
MAIN_CLASS="com.datastax.cdm.job.YugabyteMigrate"

# Spark Configuration for 20K IOPS
# Using local[10] to leave 2 cores for OS on 12-core machine
# Parallelism = 40 (4x cores) for optimal parallel processing

spark-submit \
  --master local[10] \
  --driver-memory 8G \
  --executor-memory 8G \
  --conf spark.default.parallelism=40 \
  --conf spark.sql.shuffle.partitions=40 \
  --conf spark.executor.memoryOverhead=2G \
  --conf spark.network.timeout=600s \
  --conf spark.executor.heartbeatInterval=60s \
  --conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
  --conf spark.sql.adaptive.enabled=true \
  --conf spark.sql.adaptive.coalescePartitions.enabled=true \
  --properties-file "$PROPERTIES_FILE" \
  --class "$MAIN_CLASS" \
  "$JAR_FILE"

echo ""
echo "=== Migration Started ==="
echo "Monitor logs: tail -f migration_logs/migration_summary_*.txt"
echo "Check throughput: grep 'Throughput:' migration_logs/migration_summary_*.txt"

