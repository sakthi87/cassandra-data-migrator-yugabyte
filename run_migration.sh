#!/bin/bash
# CDM Migration Command Script
# This script runs the Cassandra to YugabyteDB migration using CDM

# Set Spark home (adjust if Spark is installed differently)
if [ -z "$SPARK_HOME" ]; then
    if command -v brew &> /dev/null; then
        SPARK_HOME=$(brew --prefix apache-spark)
    else
        echo "ERROR: SPARK_HOME not set and brew not found. Please set SPARK_HOME manually."
        exit 1
    fi
fi

# Check if JAR exists
JAR_FILE="target/cassandra-data-migrator-5.5.2-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found: $JAR_FILE"
    echo "Please build the project first: mvn clean package -DskipTests"
    exit 1
fi

# Check if properties file exists
PROPERTIES_FILE="transaction-test.properties"
if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "ERROR: Properties file not found: $PROPERTIES_FILE"
    exit 1
fi

# Default values
MODE="${1:-foreground}"  # foreground or background
LOG_FILE="${2:-migration.log}"

echo "========================================================================="
echo "CDM Migration - Cassandra to YugabyteDB"
echo "========================================================================="
echo "Spark Home: $SPARK_HOME"
echo "JAR File: $JAR_FILE"
echo "Properties: $PROPERTIES_FILE"
echo "Mode: $MODE"
echo "Log File: $LOG_FILE"
echo "========================================================================="
echo ""

# Build the command
CMD="$SPARK_HOME/bin/spark-submit"
CMD="$CMD --properties-file $PROPERTIES_FILE"
CMD="$CMD --conf spark.cdm.schema.origin.keyspaceTable=\"transaction_datastore.dda_pstd_fincl_txn_cnsmr_by_accntnbr\""
CMD="$CMD --master \"local[*]\""
CMD="$CMD --driver-memory 4G"
CMD="$CMD --executor-memory 4G"
CMD="$CMD --class com.datastax.cdm.job.YugabyteMigrate"
CMD="$CMD $JAR_FILE"

if [ "$MODE" = "background" ]; then
    echo "Starting migration in BACKGROUND mode..."
    echo "Log file: $LOG_FILE"
    echo "To monitor: tail -f $LOG_FILE"
    echo ""
    nohup $CMD > "$LOG_FILE" 2>&1 &
    PID=$!
    echo "Migration started with PID: $PID"
    echo "To check status: ps -p $PID"
    echo "To stop: kill $PID"
else
    echo "Starting migration in FOREGROUND mode..."
    echo "Press Ctrl+C to stop"
    echo ""
    $CMD
fi

