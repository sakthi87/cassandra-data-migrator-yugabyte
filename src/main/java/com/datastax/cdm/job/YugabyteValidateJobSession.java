/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.cdm.job;

import java.io.Serializable;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.cql.EnhancedSession;
import com.datastax.cdm.cql.statement.OriginSelectByPartitionRangeStatement;
import com.datastax.cdm.data.PKFactory;
import com.datastax.cdm.data.Record;
import com.datastax.cdm.feature.TrackRun;
import com.datastax.cdm.properties.PropertyHelper;
import com.datastax.cdm.schema.CqlTable;
import com.datastax.cdm.yugabyte.YugabyteSession;
import com.datastax.cdm.yugabyte.error.FailedRecordLogger;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;

public class YugabyteValidateJobSession extends AbstractJobSession<PartitionRange> implements Serializable {

    private final PKFactory pkFactory;
    private final boolean isCounterTable;
    private final Integer fetchSize;
    public Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private YugabyteSession yugabyteSession;
    private FailedRecordLogger failedRecordLogger;

    // Validation counters
    private long totalValidated = 0;
    private long totalMismatched = 0;
    private long totalMissing = 0;
    private long totalExtra = 0;

    protected YugabyteValidateJobSession(CqlSession originSession, PropertyHelper propHelper) {
        super(originSession, null, propHelper); // No target CqlSession for YugabyteDB

        // Since we don't have a target CqlSession, we need to create PKFactory manually
        CqlTable cqlTableOrigin = this.originSession.getCqlTable();
        CqlTable targetCqlTable = createTargetCqlTable(cqlTableOrigin);

        // Set up the relationship between origin and target tables
        cqlTableOrigin.setOtherCqlTable(targetCqlTable);
        targetCqlTable.setOtherCqlTable(cqlTableOrigin);

        pkFactory = new PKFactory(propertyHelper, cqlTableOrigin, targetCqlTable);
        this.originSession.setPKFactory(pkFactory);

        isCounterTable = this.originSession.getCqlTable().isCounterTable();
        fetchSize = this.originSession.getCqlTable().getFetchSizeInRows();

        // Initialize YugabyteDB session for validation
        this.yugabyteSession = new YugabyteSession(propertyHelper, false);

        // Initialize failed record logger for validation results
        String logDir = propertyHelper.getString("spark.cdm.log.directory");
        if (logDir == null || logDir.trim().isEmpty()) {
            logDir = "migration_logs";
        }
        this.failedRecordLogger = new FailedRecordLogger(logDir);

        logger.info("CQL -- origin select: {}", this.originSession.getOriginSelectByPartitionRangeStatement().getCQL());
        logger.info("YugabyteDB validation session initialized");
    }

    private CqlTable createTargetCqlTable(CqlTable originTable) {
        try {
            return new CqlTable(propertyHelper, false, originSession.getCqlSession());
        } catch (Exception e) {
            logger.warn("Could not create target CqlTable, using origin table structure: {}", e.getMessage());
            return originTable;
        }
    }

    protected void processPartitionRange(PartitionRange range) {
        BigInteger min = range.getMin(), max = range.getMax();
        ThreadContext.put(THREAD_CONTEXT_LABEL, getThreadLabel(min, max));
        logger.info("ThreadID: {} Validating min: {} max: {}", Thread.currentThread().getId(), min, max);
        if (null != trackRunFeature)
            trackRunFeature.updateCdmRun(runId, min, TrackRun.RUN_STATUS.STARTED, "");

        JobCounter jobCounter = range.getJobCounter();

        try {
            OriginSelectByPartitionRangeStatement originSelectByPartitionRangeStatement = this.originSession
                    .getOriginSelectByPartitionRangeStatement();
            com.datastax.oss.driver.api.core.cql.ResultSet resultSet = originSelectByPartitionRangeStatement
                    .execute(originSelectByPartitionRangeStatement.bind(min, max));

            for (Row originRow : resultSet) {
                rateLimiterOrigin.acquire(1);
                jobCounter.increment(JobCounter.CounterType.READ);

                Record record = new Record(pkFactory.getTargetPK(originRow), originRow, null);
                if (originSelectByPartitionRangeStatement.shouldFilterRecord(record)) {
                    jobCounter.increment(JobCounter.CounterType.SKIPPED);
                    continue;
                }

                for (Record r : pkFactory.toValidRecordList(record)) {
                    try {
                        rateLimiterTarget.acquire(1);
                        ValidationResult result = validateRecord(r);

                        switch (result) {
                        case VALID:
                            jobCounter.increment(JobCounter.CounterType.VALID);
                            totalValidated++;
                            break;
                        case MISMATCHED:
                            jobCounter.increment(JobCounter.CounterType.MISMATCH);
                            totalMismatched++;
                            logger.error("Mismatch found for key: {} - {}", r.getPk(), result.getDetails());
                            break;
                        case MISSING:
                            jobCounter.increment(JobCounter.CounterType.MISSING);
                            totalMissing++;
                            logger.error("Missing record in target for key: {}", r.getPk());
                            break;
                        }
                    } catch (Exception e) {
                        logger.error("Error validating record: {}", r, e);
                        jobCounter.increment(JobCounter.CounterType.ERROR);
                    }
                }
            }

            jobCounter.increment(JobCounter.CounterType.PARTITIONS_PASSED);
            jobCounter.flush();

            // Update performance metrics
            if (failedRecordLogger != null) {
                failedRecordLogger.updateMetrics(jobCounter.getCount(JobCounter.CounterType.READ),
                        jobCounter.getCount(JobCounter.CounterType.VALID),
                        jobCounter.getCount(JobCounter.CounterType.ERROR),
                        jobCounter.getCount(JobCounter.CounterType.SKIPPED));
            }

            if (null != trackRunFeature) {
                trackRunFeature.updateCdmRun(runId, min, TrackRun.RUN_STATUS.PASS, jobCounter.getMetrics());
            }
        } catch (Exception e) {
            jobCounter.increment(JobCounter.CounterType.ERROR,
                    jobCounter.getCount(JobCounter.CounterType.READ, true)
                            - jobCounter.getCount(JobCounter.CounterType.VALID, true)
                            - jobCounter.getCount(JobCounter.CounterType.MISMATCH, true)
                            - jobCounter.getCount(JobCounter.CounterType.MISSING, true)
                            - jobCounter.getCount(JobCounter.CounterType.SKIPPED, true));
            jobCounter.increment(JobCounter.CounterType.PARTITIONS_FAILED);
            logger.error("Error with PartitionRange -- ThreadID: {} Processing min: {} max: {}",
                    Thread.currentThread().getId(), min, max, e);
            logger.error("Error stats " + jobCounter.getMetrics(true));
            jobCounter.flush();
            if (null != trackRunFeature) {
                trackRunFeature.updateCdmRun(runId, min, TrackRun.RUN_STATUS.FAIL, jobCounter.getMetrics());
            }
        } finally {
            ThreadContext.remove(THREAD_CONTEXT_LABEL);
        }
    }

    private ValidationResult validateRecord(Record record) {
        try (Connection connection = yugabyteSession.getConnection()) {
            // Build SELECT query for the record
            String selectSQL = buildSelectQuery(record);

            try (PreparedStatement statement = connection.prepareStatement(selectSQL)) {
                // Bind primary key values
                bindPrimaryKey(statement, record);

                try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return ValidationResult.MISSING;
                    }

                    // Compare all column values
                    return compareRecordData(record, resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error("SQL error during validation for record: {}", record.getPk(), e);
            return ValidationResult.ERROR;
        }
    }

    private String buildSelectQuery(Record record) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(yugabyteSession.getYugabyteTable().getKeyspaceTable());
        sql.append(" WHERE ");

        List<String> primaryKeys = yugabyteSession.getYugabyteTable().getPrimaryKeyNames();
        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0)
                sql.append(" AND ");
            sql.append(primaryKeys.get(i)).append(" = ?");
        }

        return sql.toString();
    }

    private void bindPrimaryKey(PreparedStatement statement, Record record) throws SQLException {
        List<String> primaryKeys = yugabyteSession.getYugabyteTable().getPrimaryKeyNames();
        Row originRow = record.getOriginRow();

        for (int i = 0; i < primaryKeys.size(); i++) {
            String columnName = primaryKeys.get(i);
            Object value = originRow.getObject(columnName);
            statement.setObject(i + 1, value);
        }
    }

    private ValidationResult compareRecordData(Record record, java.sql.ResultSet targetResult) throws SQLException {
        Row originRow = record.getOriginRow();
        List<String> columnNames = yugabyteSession.getYugabyteTable().getAllColumnNames();

        List<String> mismatches = new ArrayList<>();

        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            Object originValue = originRow.getObject(columnName);
            Object targetValue = targetResult.getObject(i + 1);

            if (!valuesEqual(originValue, targetValue)) {
                mismatches
                        .add(String.format("Column %s: Origin='%s' Target='%s'", columnName, originValue, targetValue));
            }
        }

        if (mismatches.isEmpty()) {
            return ValidationResult.VALID;
        } else {
            return ValidationResult.MISMATCHED.withDetails(String.join("; ", mismatches));
        }
    }

    private boolean valuesEqual(Object origin, Object target) {
        if (origin == null && target == null)
            return true;
        if (origin == null || target == null)
            return false;
        return origin.toString().equals(target.toString());
    }

    @Override
    public void close() {
        if (failedRecordLogger != null) {
            failedRecordLogger.close();
        }
        if (yugabyteSession != null) {
            yugabyteSession.close();
        }
        super.close();
    }

    // Validation result enum
    private enum ValidationResult {
        VALID, MISMATCHED, MISSING, ERROR;

        private String details = "";

        public ValidationResult withDetails(String details) {
            this.details = details;
            return this;
        }

        public String getDetails() {
            return details;
        }
    }
}
