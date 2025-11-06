#!/bin/bash
# =============================================================================
# Cassandra to Yugabyte YCQL Migration Commands
# =============================================================================
# This script provides ready-to-use commands for migrating from DataStax
# Cassandra to YugabyteDB YCQL (Cassandra-compatible interface)
#
# Usage:
#   1. Update cassandra-to-ycql-migration.properties with your connection details
#   2. Run the appropriate command below
# =============================================================================

# Set your table name here
TABLE_NAME="customer_datastore.customer_mtrc_by_lpid"
PROPERTIES_FILE="cassandra-to-ycql-migration.properties"
JAR_FILE="cassandra-data-migrator-5.5.2-SNAPSHOT.jar"

# =============================================================================
# BASIC MIGRATION COMMAND
# =============================================================================
echo "Basic Migration Command:"
echo "----------------------"
echo "spark-submit --properties-file ${PROPERTIES_FILE} \\"
echo "--conf spark.cdm.schema.origin.keyspaceTable=\"${TABLE_NAME}\" \\"
echo "--master \"local[*]\" --driver-memory 25G --executor-memory 25G \\"
echo "--class com.datastax.cdm.job.Migrate ${JAR_FILE} \\"
echo "&> ycql_migration_\$(date +%Y%m%d_%H_%M).txt"
echo ""

# =============================================================================
# MIGRATION WITH RUN TRACKING
# =============================================================================
echo "Migration with Run Tracking:"
echo "---------------------------"
echo "spark-submit --properties-file ${PROPERTIES_FILE} \\"
echo "--conf spark.cdm.schema.origin.keyspaceTable=\"${TABLE_NAME}\" \\"
echo "--conf spark.cdm.trackRun=true \\"
echo "--master \"local[*]\" --driver-memory 25G --executor-memory 25G \\"
echo "--class com.datastax.cdm.job.Migrate ${JAR_FILE} \\"
echo "&> ycql_migration_\$(date +%Y%m%d_%H_%M).txt"
echo ""

# =============================================================================
# PERFORMANCE-TUNED MIGRATION
# =============================================================================
echo "Performance-Tuned Migration:"
echo "---------------------------"
echo "spark-submit --properties-file ${PROPERTIES_FILE} \\"
echo "--conf spark.cdm.schema.origin.keyspaceTable=\"${TABLE_NAME}\" \\"
echo "--conf spark.cdm.perfops.numParts=1000 \\"
echo "--conf spark.cdm.perfops.batchSize=5000 \\"
echo "--conf spark.cdm.perfops.fetchSizeInRows=5000 \\"
echo "--conf spark.cdm.perfops.ratelimit.origin=10000 \\"
echo "--conf spark.cdm.perfops.ratelimit.target=10000 \\"
echo "--master \"local[*]\" --driver-memory 25G --executor-memory 25G \\"
echo "--class com.datastax.cdm.job.Migrate ${JAR_FILE} \\"
echo "&> ycql_migration_\$(date +%Y%m%d_%H_%M).txt"
echo ""

# =============================================================================
# VALIDATION COMMAND
# =============================================================================
echo "Validation Command (DiffData):"
echo "------------------------------"
echo "spark-submit --properties-file ${PROPERTIES_FILE} \\"
echo "--conf spark.cdm.schema.origin.keyspaceTable=\"${TABLE_NAME}\" \\"
echo "--master \"local[*]\" --driver-memory 25G --executor-memory 25G \\"
echo "--class com.datastax.cdm.job.DiffData ${JAR_FILE} \\"
echo "&> ycql_validation_\$(date +%Y%m%d_%H_%M).txt"
echo ""

# =============================================================================
# QUICK COPY COMMANDS (Ready to Execute)
# =============================================================================
# Uncomment the command you want to use and modify TABLE_NAME above

# Basic Migration (uncomment to use):
# spark-submit --properties-file cassandra-to-ycql-migration.properties \
# --conf spark.cdm.schema.origin.keyspaceTable="${TABLE_NAME}" \
# --master "local[*]" --driver-memory 25G --executor-memory 25G \
# --class com.datastax.cdm.job.Migrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
# &> ycql_migration_$(date +%Y%m%d_%H_%M).txt

# Migration with Tracking (uncomment to use):
# spark-submit --properties-file cassandra-to-ycql-migration.properties \
# --conf spark.cdm.schema.origin.keyspaceTable="${TABLE_NAME}" \
# --conf spark.cdm.trackRun=true \
# --master "local[*]" --driver-memory 25G --executor-memory 25G \
# --class com.datastax.cdm.job.Migrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
# &> ycql_migration_$(date +%Y%m%d_%H_%M).txt

# Validation (uncomment to use):
# spark-submit --properties-file cassandra-to-ycql-migration.properties \
# --conf spark.cdm.schema.origin.keyspaceTable="${TABLE_NAME}" \
# --master "local[*]" --driver-memory 25G --executor-memory 25G \
# --class com.datastax.cdm.job.DiffData cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
# &> ycql_validation_$(date +%Y%m%d_%H_%M).txt

