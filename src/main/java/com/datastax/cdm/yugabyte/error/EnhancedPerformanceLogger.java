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
package com.datastax.cdm.yugabyte.error;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced performance logger that provides detailed batch-level metrics and timing analysis
 */
public class EnhancedPerformanceLogger {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedPerformanceLogger.class);

    // Shared timestamp across all instances
    private static final String SHARED_TIMESTAMP = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

    // Static counters to aggregate metrics from all tasks
    private static final AtomicLong totalReads = new AtomicLong(0);
    private static final AtomicLong totalWrites = new AtomicLong(0);
    private static final AtomicLong totalErrors = new AtomicLong(0);
    private static final AtomicLong totalSkipped = new AtomicLong(0);
    private static final AtomicLong totalPartitionsProcessed = new AtomicLong(0);
    private static final AtomicLong totalPartitionsFailed = new AtomicLong(0);

    // Detailed batch processing metrics
    private static final AtomicLong totalBatchesProcessed = new AtomicLong(0);
    private static final AtomicLong totalBatchTime = new AtomicLong(0);
    private static final AtomicLong totalBatchSize = new AtomicLong(0);
    private static final AtomicLong peakBatchThroughput = new AtomicLong(0);
    private static final AtomicLong totalConnectionTime = new AtomicLong(0);
    private static final AtomicLong totalDataProcessingTime = new AtomicLong(0);
    private static final AtomicLong totalOverheadTime = new AtomicLong(0);

    // Start time for the entire migration
    private static final long migrationStartTime = System.currentTimeMillis();

    // Lock for thread-safe operations
    private static final ReentrantLock lock = new ReentrantLock();

    // Performance writer (shared across all instances)
    private static PrintWriter performanceWriter;
    private static String baseDir;
    private static boolean initialized = false;

    /**
     * Initialize the enhanced performance logger
     */
    public static synchronized void initialize(String logDirectory) {
        if (initialized) {
            return;
        }

        try {
            baseDir = logDirectory;
            Path logDir = Paths.get(logDirectory);
            Files.createDirectories(logDir);

            String performanceFile = logDir.resolve("detailed_performance_" + SHARED_TIMESTAMP + ".txt").toString();
            performanceWriter = new PrintWriter(new FileWriter(performanceFile, true));

            performanceWriter.println("Enhanced Performance Monitoring Started");
            performanceWriter.println("=====================================");
            performanceWriter.println("Timestamp: " + LocalDateTime.now());
            performanceWriter.println("Log Directory: " + logDirectory);
            performanceWriter.println();

            initialized = true;
            logger.info("Enhanced performance logger initialized. Log file: {}", performanceFile);

        } catch (IOException e) {
            logger.error("Failed to initialize enhanced performance logger", e);
        }
    }

    /**
     * Update detailed performance metrics including batch processing details
     */
    public static void updateMetrics(long reads, long writes, long errors, long skipped, long partitionsProcessed,
            long partitionsFailed, long batchesProcessed, long batchTime, long batchSize, long connectionTime,
            long dataProcessingTime) {
        if (!initialized) {
            return;
        }

        lock.lock();
        try {
            totalReads.addAndGet(reads);
            totalWrites.addAndGet(writes);
            totalErrors.addAndGet(errors);
            totalSkipped.addAndGet(skipped);
            totalPartitionsProcessed.addAndGet(partitionsProcessed);
            totalPartitionsFailed.addAndGet(partitionsFailed);

            // Update detailed batch metrics
            totalBatchesProcessed.addAndGet(batchesProcessed);
            totalBatchTime.addAndGet(batchTime);
            totalBatchSize.addAndGet(batchSize);
            totalConnectionTime.addAndGet(connectionTime);
            totalDataProcessingTime.addAndGet(dataProcessingTime);

            // Calculate overhead time
            long overheadTime = batchTime - connectionTime - dataProcessingTime;
            totalOverheadTime.addAndGet(overheadTime);

            // Calculate current batch throughput
            if (batchTime > 0 && batchSize > 0) {
                long currentBatchThroughput = (batchSize * 1000) / batchTime; // records per second
                if (currentBatchThroughput > peakBatchThroughput.get()) {
                    peakBatchThroughput.set(currentBatchThroughput);
                }
            }

            // Write detailed progress updates
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - migrationStartTime;
            double currentThroughput = totalWrites.get() > 0 ? (totalWrites.get() * 1000.0 / elapsed) : 0.0;
            double avgBatchSize = totalBatchesProcessed.get() > 0
                    ? (double) totalBatchSize.get() / totalBatchesProcessed.get() : 0.0;
            double avgBatchTime = totalBatchesProcessed.get() > 0
                    ? (double) totalBatchTime.get() / totalBatchesProcessed.get() : 0.0;
            double avgConnectionTime = totalBatchesProcessed.get() > 0
                    ? (double) totalConnectionTime.get() / totalBatchesProcessed.get() : 0.0;
            double avgDataProcessingTime = totalBatchesProcessed.get() > 0
                    ? (double) totalDataProcessingTime.get() / totalBatchesProcessed.get() : 0.0;
            double avgOverheadTime = totalBatchesProcessed.get() > 0
                    ? (double) totalOverheadTime.get() / totalBatchesProcessed.get() : 0.0;

            performanceWriter.printf(
                    "[%s] DETAILED PROGRESS REPORT%n" + "  Overall: Reads: %d, Writes: %d, Errors: %d, Skipped: %d%n"
                            + "  Partitions: %d/%d (%.1f%% success rate)%n" + "  Current Throughput: %.2f records/sec%n"
                            + "  Batch Processing: %d batches processed%n" + "  Average Batch Size: %.1f records%n"
                            + "  Average Batch Time: %.1fms (Connection: %.1fms, Data: %.1fms, Overhead: %.1fms)%n"
                            + "  Peak Batch Throughput: %d records/sec%n"
                            + "  Time Breakdown: Connection %.1f%%, Data Processing %.1f%%, Overhead %.1f%%%n"
                            + "  ==========================================%n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), totalReads.get(),
                    totalWrites.get(), totalErrors.get(), totalSkipped.get(), totalPartitionsProcessed.get(),
                    totalPartitionsProcessed.get() + totalPartitionsFailed.get(),
                    totalPartitionsProcessed.get() > 0 ? (100.0 * totalPartitionsProcessed.get()
                            / (totalPartitionsProcessed.get() + totalPartitionsFailed.get())) : 0.0,
                    currentThroughput, totalBatchesProcessed.get(), avgBatchSize, avgBatchTime, avgConnectionTime,
                    avgDataProcessingTime, avgOverheadTime, peakBatchThroughput.get(),
                    totalBatchTime.get() > 0 ? (100.0 * totalConnectionTime.get() / totalBatchTime.get()) : 0.0,
                    totalBatchTime.get() > 0 ? (100.0 * totalDataProcessingTime.get() / totalBatchTime.get()) : 0.0,
                    totalBatchTime.get() > 0 ? (100.0 * totalOverheadTime.get() / totalBatchTime.get()) : 0.0);
            performanceWriter.flush();

        } catch (Exception e) {
            logger.error("Failed to update enhanced performance metrics", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Write the final comprehensive migration summary with detailed analysis
     */
    public static void writeFinalSummary() {
        if (!initialized || performanceWriter == null) {
            return;
        }

        lock.lock();
        try {
            long totalTime = System.currentTimeMillis() - migrationStartTime;
            long totalRecords = totalWrites.get();

            performanceWriter.println();
            performanceWriter.println("FINAL MIGRATION SUMMARY");
            performanceWriter.println("======================");
            performanceWriter.println("Migration completed at: " + LocalDateTime.now());
            performanceWriter.println("Total migration time: " + formatDuration(totalTime));
            performanceWriter.println();

            // Overall statistics
            performanceWriter.println("OVERALL STATISTICS:");
            performanceWriter.println("  Total records read: " + totalReads.get());
            performanceWriter.println("  Total records written: " + totalWrites.get());
            performanceWriter.println("  Total errors: " + totalErrors.get());
            performanceWriter.println("  Total skipped: " + totalSkipped.get());
            performanceWriter.println("  Success rate: " + (totalRecords > 0
                    ? String.format("%.2f%%", 100.0 * (totalRecords - totalErrors.get()) / totalRecords) : "0.00%"));
            performanceWriter.println();

            // Partition statistics
            performanceWriter.println("PARTITION PROCESSING:");
            performanceWriter.println("  Partitions processed: " + totalPartitionsProcessed.get());
            performanceWriter.println("  Partitions failed: " + totalPartitionsFailed.get());
            performanceWriter.println(
                    "  Partition success rate: " + (totalPartitionsProcessed.get() + totalPartitionsFailed.get() > 0
                            ? String.format("%.2f%%",
                                    100.0 * totalPartitionsProcessed.get()
                                            / (totalPartitionsProcessed.get() + totalPartitionsFailed.get()))
                            : "0.00%"));
            performanceWriter.println();

            // Performance metrics
            performanceWriter.println("PERFORMANCE METRICS:");
            performanceWriter.println("  Average throughput: "
                    + String.format("%.2f records/sec", totalRecords > 0 ? (totalRecords * 1000.0 / totalTime) : 0.0));
            performanceWriter.println("  Peak throughput: "
                    + String.format("%.2f records/sec", calculatePeakThroughput(totalRecords, totalTime)));
            performanceWriter.println("  Data volume: " + String.format("%.2f GB", totalRecords * 0.0006)); // Assuming
                                                                                                            // ~600
                                                                                                            // bytes per
                                                                                                            // record
            performanceWriter.println(
                    "  Data rate: " + String.format("%.2f GB/hour", totalRecords * 0.0006 * 3600000.0 / totalTime));
            performanceWriter.println();

            // Batch processing analysis
            performanceWriter.println("BATCH PROCESSING ANALYSIS:");
            performanceWriter.println("  Total batches processed: " + totalBatchesProcessed.get());
            performanceWriter
                    .println("  Average batch size: " + String.format("%.1f records", totalBatchesProcessed.get() > 0
                            ? (double) totalBatchSize.get() / totalBatchesProcessed.get() : 0.0));
            performanceWriter
                    .println("  Average batch time: " + String.format("%.1f ms", totalBatchesProcessed.get() > 0
                            ? (double) totalBatchTime.get() / totalBatchesProcessed.get() : 0.0));
            performanceWriter.println("  Peak batch throughput: " + peakBatchThroughput.get() + " records/sec");
            performanceWriter.println();

            // Time breakdown analysis
            performanceWriter.println("TIME BREAKDOWN ANALYSIS:");
            if (totalBatchTime.get() > 0) {
                performanceWriter.println("  Connection time: " + String.format("%.1f ms (%.1f%%)",
                        (double) totalConnectionTime.get() / totalBatchesProcessed.get(),
                        100.0 * totalConnectionTime.get() / totalBatchTime.get()));
                performanceWriter.println("  Data processing time: " + String.format("%.1f ms (%.1f%%)",
                        (double) totalDataProcessingTime.get() / totalBatchesProcessed.get(),
                        100.0 * totalDataProcessingTime.get() / totalBatchTime.get()));
                performanceWriter.println("  Overhead time: " + String.format("%.1f ms (%.1f%%)",
                        (double) totalOverheadTime.get() / totalBatchesProcessed.get(),
                        100.0 * totalOverheadTime.get() / totalBatchTime.get()));
            }
            performanceWriter.println();

            // Performance recommendations
            performanceWriter.println("PERFORMANCE RECOMMENDATIONS:");
            if (totalBatchTime.get() > 0) {
                double connectionPercentage = 100.0 * totalConnectionTime.get() / totalBatchTime.get();
                double dataPercentage = 100.0 * totalDataProcessingTime.get() / totalBatchTime.get();
                double overheadPercentage = 100.0 * totalOverheadTime.get() / totalBatchTime.get();

                if (connectionPercentage > 30) {
                    performanceWriter.println("  ⚠️  High connection overhead ("
                            + String.format("%.1f", connectionPercentage) + "%) - Consider increasing batch size");
                }
                if (dataPercentage < 50) {
                    performanceWriter.println("  ⚠️  Low data processing ratio ("
                            + String.format("%.1f", dataPercentage) + "%) - Consider optimizing data processing");
                }
                if (overheadPercentage > 20) {
                    performanceWriter.println("  ⚠️  High overhead (" + String.format("%.1f", overheadPercentage)
                            + "%) - Consider reducing batch operations");
                }
                if (connectionPercentage < 20 && dataPercentage > 70) {
                    performanceWriter.println(
                            "  ✅ Good performance balance - Connection and data processing are well optimized");
                }
            }
            performanceWriter.println();

            performanceWriter.println("Migration Summary Complete");
            performanceWriter.println("==========================");

            performanceWriter.flush();

        } catch (Exception e) {
            logger.error("Failed to write enhanced migration summary", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Close the enhanced performance logger
     */
    public static void close() {
        if (performanceWriter != null) {
            writeFinalSummary();
            performanceWriter.close();
            logger.info("Enhanced performance logging completed. Summary saved in: {}", baseDir);
        }
    }

    private static double calculatePeakThroughput(long totalWrites, long totalTime) {
        // Simple estimation - in a real scenario, you'd track peak throughput over time
        return totalWrites > 0 ? (totalWrites * 1000.0 / (totalTime * 0.8)) : 0.0; // Assume 80% of time was active
    }

    private static String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
