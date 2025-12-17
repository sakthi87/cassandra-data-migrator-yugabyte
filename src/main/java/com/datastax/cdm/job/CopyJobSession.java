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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.cql.statement.OriginSelectByPartitionRangeStatement;
import com.datastax.cdm.cql.statement.TargetSelectByPKStatement;
import com.datastax.cdm.cql.statement.TargetUpsertStatement;
import com.datastax.cdm.data.PKFactory;
import com.datastax.cdm.data.Record;
import com.datastax.cdm.feature.TrackRun;
import com.datastax.cdm.properties.PropertyHelper;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

public class CopyJobSession extends AbstractJobSession<PartitionRange> {

    private final PKFactory pkFactory;
    private final boolean isCounterTable;
    private final Integer fetchSize;
    private final Integer batchSize;
    public Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private TargetUpsertStatement targetUpsertStatement;
    private TargetSelectByPKStatement targetSelectByPKStatement;
    
    // Phase 3: Non-blocking pipeline - track pending async writes
    private static final int MAX_PENDING_WRITES = 100; // Backpressure limit
    private final ConcurrentLinkedQueue<CompletableFuture<AsyncResultSet>> pendingWrites = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingWriteCount = new AtomicInteger(0);

    protected CopyJobSession(CqlSession originSession, CqlSession targetSession, PropertyHelper propHelper) {
        super(originSession, targetSession, propHelper);
        pkFactory = this.originSession.getPKFactory();
        isCounterTable = this.originSession.getCqlTable().isCounterTable();
        fetchSize = this.originSession.getCqlTable().getFetchSizeInRows();
        batchSize = this.originSession.getCqlTable().getBatchSize();

        logger.info("CQL -- origin select: {}", this.originSession.getOriginSelectByPartitionRangeStatement().getCQL());
        logger.info("CQL -- target select: {}", this.targetSession.getTargetSelectByPKStatement().getCQL());
        logger.info("CQL -- target upsert: {}", this.targetSession.getTargetUpsertStatement().getCQL());
    }

    protected void processPartitionRange(PartitionRange range) {
        BigInteger min = range.getMin(), max = range.getMax();
        ThreadContext.put(THREAD_CONTEXT_LABEL, getThreadLabel(min, max));
        logger.info("ThreadID: {} Processing min: {} max: {}", Thread.currentThread().getId(), min, max);
        if (null != trackRunFeature)
            trackRunFeature.updateCdmRun(runId, min, TrackRun.RUN_STATUS.STARTED, "");

        BatchStatement batch = BatchStatement.newInstance(BatchType.UNLOGGED);
        JobCounter jobCounter = range.getJobCounter();

        try {
            OriginSelectByPartitionRangeStatement originSelectByPartitionRangeStatement = this.originSession
                    .getOriginSelectByPartitionRangeStatement();
            targetUpsertStatement = this.targetSession.getTargetUpsertStatement();
            targetSelectByPKStatement = this.targetSession.getTargetSelectByPKStatement();
            ResultSet resultSet = originSelectByPartitionRangeStatement
                    .execute(originSelectByPartitionRangeStatement.bind(min, max));
            Collection<CompletionStage<AsyncResultSet>> writeResults = new ArrayList<>();

            for (Row originRow : resultSet) {
                rateLimiterOrigin.acquire(1);
                jobCounter.increment(JobCounter.CounterType.READ);

                Record record = new Record(pkFactory.getTargetPK(originRow), originRow, null);
                if (originSelectByPartitionRangeStatement.shouldFilterRecord(record)) {
                    jobCounter.increment(JobCounter.CounterType.SKIPPED);
                    continue;
                }

                for (Record r : pkFactory.toValidRecordList(record)) {
                    BoundStatement boundUpsert = bind(r);
                    if (null == boundUpsert) {
                        jobCounter.increment(JobCounter.CounterType.SKIPPED);
                        continue;
                    }

                    // Phase 3: Apply backpressure - wait if too many pending writes
                    waitForBackpressure();
                    
                    // Phase 2: Rate limiting moved to batch level (removed per-operation)
                    batch = writeAsync(batch, writeResults, boundUpsert);
                    jobCounter.increment(JobCounter.CounterType.UNFLUSHED);

                    if (jobCounter.getCount(JobCounter.CounterType.UNFLUSHED) > fetchSize) {
                        // Phase 3: Non-blocking flush - submit async and continue
                        flushAsync(batch, writeResults);
                        jobCounter.increment(JobCounter.CounterType.WRITE,
                                jobCounter.getCount(JobCounter.CounterType.UNFLUSHED, true));
                        jobCounter.reset(JobCounter.CounterType.UNFLUSHED);
                        batch = BatchStatement.newInstance(BatchType.UNLOGGED);
                    }
                }
            }

            // Phase 3: Final flush - submit remaining batch async
            flushAsync(batch, writeResults);
            
            // Phase 3: Wait for all pending writes to complete
            waitForAllPendingWrites();
            jobCounter.increment(JobCounter.CounterType.WRITE,
                    jobCounter.getCount(JobCounter.CounterType.UNFLUSHED, true));
            jobCounter.increment(JobCounter.CounterType.PARTITIONS_PASSED);
            jobCounter.reset(JobCounter.CounterType.UNFLUSHED);
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
        }
    }

    /**
     * Phase 1: Fixed blocking async - wait for all results in parallel instead of sequentially
     * Phase 2: Rate limiting moved to batch level (here)
     * Phase 3: Non-blocking - submit async and return immediately (NO blocking wait)
     */
    private void flushAsync(BatchStatement batch, Collection<CompletionStage<AsyncResultSet>> writeResults) {
        // Phase 1: Process any existing writeResults in parallel (if any)
        if (!writeResults.isEmpty()) {
            CompletableFuture<?>[] futures = writeResults.stream()
                .map(writeResult -> writeResult.toCompletableFuture())
                .toArray(CompletableFuture[]::new);
            
            // Wait for all to complete in parallel (not sequentially)
            CompletableFuture.allOf(futures).join();
            
            // Process results
            writeResults.stream().forEach(writeResult -> {
                try {
                    writeResult.toCompletableFuture().join().one();
                } catch (Exception e) {
                    logger.error("Error processing async result in flush", e);
                }
            });
            writeResults.clear();
        }
        
        // Phase 2 & 3: Submit new batch async (non-blocking)
        if (batch.size() > 0) {
            // Phase 2: Batch-level rate limiting (instead of per-operation)
            int batchRecords = batch.size();
            rateLimiterTarget.acquire(batchRecords);
            
            // Phase 3: Submit async and track in pending queue (non-blocking - NO wait)
            CompletableFuture<AsyncResultSet> future = targetUpsertStatement.executeAsync(batch)
                .toCompletableFuture();
            
            // Add error handling callback
            future.whenComplete((result, throwable) -> {
                pendingWriteCount.decrementAndGet();
                if (throwable != null) {
                    logger.error("Error in async write batch", throwable);
                } else {
                    try {
                        result.one(); // Process result
                    } catch (Exception e) {
                        logger.error("Error processing async result", e);
                    }
                }
            });
            
            pendingWrites.add(future);
            pendingWriteCount.incrementAndGet();
        }
    }
    
    /**
     * Phase 3: Backpressure - wait if too many pending writes
     */
    private void waitForBackpressure() {
        int maxRetries = 100;
        int retryCount = 0;
        while (pendingWriteCount.get() >= MAX_PENDING_WRITES && retryCount < maxRetries) {
            try {
                Thread.sleep(10); // Wait 10ms
                retryCount++;
                // Clean up completed futures
                pendingWrites.removeIf(f -> f.isDone());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Phase 3: Wait for all pending writes to complete
     */
    private void waitForAllPendingWrites() {
        // Clean up and wait for all pending writes
        CompletableFuture<?>[] futures = pendingWrites.stream()
            .filter(f -> !f.isDone())
            .toArray(CompletableFuture[]::new);
        
        if (futures.length > 0) {
            CompletableFuture.allOf(futures).join();
        }
        
        pendingWrites.clear();
        pendingWriteCount.set(0);
    }
    

    private BoundStatement bind(Record r) {
        if (isCounterTable) {
            // Phase 2: Rate limiting for counter table reads (keep per-operation for reads)
            rateLimiterTarget.acquire(1);
            Record targetRecord = targetSelectByPKStatement.getRecord(r.getPk());
            if (null != targetRecord) {
                r.setTargetRow(targetRecord.getTargetRow());
            }
        }
        return targetUpsertStatement.bindRecord(r);
    }

    private BatchStatement writeAsync(BatchStatement batch, Collection<CompletionStage<AsyncResultSet>> writeResults,
            BoundStatement boundUpsert) {
        if (batchSize > 1) {
            batch = batch.add(boundUpsert);
            if (batch.size() >= batchSize) {
                // Phase 2: Batch-level rate limiting
                rateLimiterTarget.acquire(batch.size());
                
                // Phase 3: Submit async and track (non-blocking)
                CompletableFuture<AsyncResultSet> future = targetUpsertStatement.executeAsync(batch)
                    .toCompletableFuture();
                
                // Add error handling callback
                future.whenComplete((result, throwable) -> {
                    pendingWriteCount.decrementAndGet();
                    if (throwable != null) {
                        logger.error("Error in async write batch from writeAsync", throwable);
                    } else {
                        try {
                            result.one();
                        } catch (Exception e) {
                            logger.error("Error processing async result from writeAsync", e);
                        }
                    }
                });
                
                pendingWrites.add(future);
                pendingWriteCount.incrementAndGet();
                writeResults.add(future);
                return BatchStatement.newInstance(BatchType.UNLOGGED);
            }
            return batch;
        } else {
            // Phase 2: Per-operation rate limiting for single-record batches
            rateLimiterTarget.acquire(1);
            
            // Phase 3: Submit async and track (non-blocking)
            CompletableFuture<AsyncResultSet> future = targetUpsertStatement.executeAsync(boundUpsert)
                .toCompletableFuture();
            
            future.whenComplete((result, throwable) -> {
                pendingWriteCount.decrementAndGet();
                if (throwable != null) {
                    logger.error("Error in async write from writeAsync", throwable);
                } else {
                    try {
                        result.one();
                    } catch (Exception e) {
                        logger.error("Error processing async result from writeAsync", e);
                    }
                }
            });
            
            pendingWrites.add(future);
            pendingWriteCount.incrementAndGet();
            writeResults.add(future);
            return batch;
        }
    }

}
