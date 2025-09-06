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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.cql.EnhancedSession;
import com.datastax.cdm.cql.statement.OriginSelectByPartitionRangeStatement;
import com.datastax.cdm.data.PKFactory;
import com.datastax.cdm.data.Record;
import com.datastax.cdm.feature.TrackRun;
import com.datastax.cdm.properties.PropertyHelper;
import com.datastax.cdm.yugabyte.YugabyteSession;
import com.datastax.cdm.yugabyte.statement.YugabyteUpsertStatement;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

public class YugabyteCopyJobSession extends AbstractJobSession<PartitionRange> implements Serializable {

    private final PKFactory pkFactory;
    private final boolean isCounterTable;
    private final Integer fetchSize;
    private final Integer batchSize;
    public Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private YugabyteUpsertStatement yugabyteUpsertStatement;
    private YugabyteSession yugabyteSession;

    protected YugabyteCopyJobSession(CqlSession originSession, PropertyHelper propHelper) {
        super(originSession, null, propHelper); // No target CqlSession for YugabyteDB
        pkFactory = this.originSession.getPKFactory();
        isCounterTable = this.originSession.getCqlTable().isCounterTable();
        fetchSize = this.originSession.getCqlTable().getFetchSizeInRows();
        batchSize = this.originSession.getCqlTable().getBatchSize();

        // Initialize YugabyteDB session
        this.yugabyteSession = new YugabyteSession(propertyHelper, false);
        this.yugabyteUpsertStatement = this.yugabyteSession.getYugabyteUpsertStatement();

        logger.info("CQL -- origin select: {}", this.originSession.getOriginSelectByPartitionRangeStatement().getCQL());
        logger.info("SQL -- yugabyte upsert: {}", this.yugabyteUpsertStatement.getSQL());
    }

    protected void processPartitionRange(PartitionRange range) {
        BigInteger min = range.getMin(), max = range.getMax();
        ThreadContext.put(THREAD_CONTEXT_LABEL, getThreadLabel(min, max));
        logger.info("ThreadID: {} Processing min: {} max: {}", Thread.currentThread().getId(), min, max);
        if (null != trackRunFeature)
            trackRunFeature.updateCdmRun(runId, min, TrackRun.RUN_STATUS.STARTED, "");

        JobCounter jobCounter = range.getJobCounter();

        try {
            OriginSelectByPartitionRangeStatement originSelectByPartitionRangeStatement = this.originSession
                    .getOriginSelectByPartitionRangeStatement();
            ResultSet resultSet = originSelectByPartitionRangeStatement
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
                        yugabyteUpsertStatement.execute(r);
                        jobCounter.increment(JobCounter.CounterType.WRITE);
                    } catch (SQLException e) {
                        logger.error("Error writing record to YugabyteDB: {}", r, e);
                        jobCounter.increment(JobCounter.CounterType.ERROR);
                    }
                }
            }

            jobCounter.increment(JobCounter.CounterType.PARTITIONS_PASSED);
            jobCounter.flush();
            if (null != trackRunFeature) {
                trackRunFeature.updateCdmRun(runId, min, TrackRun.RUN_STATUS.PASS, jobCounter.getMetrics());
            }
        } catch (Exception e) {
            jobCounter.increment(JobCounter.CounterType.ERROR,
                    jobCounter.getCount(JobCounter.CounterType.READ, true)
                            - jobCounter.getCount(JobCounter.CounterType.WRITE, true)
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

    @Override
    public void close() {
        if (yugabyteSession != null) {
            yugabyteSession.close();
        }
        super.close();
    }
}
