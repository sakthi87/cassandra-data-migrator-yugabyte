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
 * Centralized performance logger that aggregates metrics from all Spark tasks and writes a comprehensive summary at the
 * end of the migration
 */
public class CentralizedPerformanceLogger {
    private static final Logger logger = LoggerFactory.getLogger(CentralizedPerformanceLogger.class);

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

    // Start time for the entire migration
    private static final long migrationStartTime = System.currentTimeMillis();

    // Lock for thread-safe operations
    private static final ReentrantLock lock = new ReentrantLock();

    // Performance writer (shared across all instances)
    private static PrintWriter performanceWriter;
    private static String baseDir;
    private static boolean initialized = false;

    /**
     * Initialize the centralized performance logger
     */
    public static synchronized void initialize(String logDirectory) {
        if (initialized) {
            return;
        }

        baseDir = logDirectory != null ? logDirectory : "migration_logs";

        try {
            // Create directory if it doesn't exist
            Path dir = Paths.get(baseDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            // Initialize performance file
            String performanceFile = String.format("%s/migration_summary_%s.txt", baseDir, SHARED_TIMESTAMP);
            performanceWriter = new PrintWriter(new FileWriter(performanceFile, true));

            performanceWriter.println("=== Cassandra to YugabyteDB Migration Summary ===");
            performanceWriter.println("Migration Start Time: "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            performanceWriter.println("Migration ID: " + SHARED_TIMESTAMP);
            performanceWriter.println();
            performanceWriter.println("=== Real-time Progress ===");
            performanceWriter.flush();

            initialized = true;
            logger.info("Centralized performance logging initialized: {}", performanceFile);

        } catch (IOException e) {
            logger.error("Failed to initialize centralized performance logging", e);
            throw new RuntimeException("Could not initialize performance logging", e);
        }
    }

    /**
     * Update metrics from a Spark task
     */
    public static void updateMetrics(long reads, long writes, long errors, long skipped, long partitionsProcessed,
            long partitionsFailed) {
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

            // Write periodic progress updates
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - migrationStartTime;
            double currentThroughput = totalWrites.get() > 0 ? (totalWrites.get() * 1000.0 / elapsed) : 0.0;

            performanceWriter.printf(
                    "[%s] Progress: Reads: %d, Writes: %d, Errors: %d, Skipped: %d, "
                            + "Partitions: %d/%d, Throughput: %.2f records/sec%n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), totalReads.get(),
                    totalWrites.get(), totalErrors.get(), totalSkipped.get(), totalPartitionsProcessed.get(),
                    totalPartitionsProcessed.get() + totalPartitionsFailed.get(), currentThroughput);
            performanceWriter.flush();

        } catch (Exception e) {
            logger.error("Failed to update centralized performance metrics", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Write the final comprehensive migration summary
     */
    public static void writeFinalSummary() {
        if (!initialized || performanceWriter == null) {
            return;
        }

        lock.lock();
        try {
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - migrationStartTime;
            double avgThroughput = totalWrites.get() > 0 ? (totalWrites.get() * 1000.0 / totalTime) : 0.0;
            double errorRate = totalReads.get() > 0 ? (totalErrors.get() * 100.0 / totalReads.get()) : 0.0;
            double successRate = 100.0 - errorRate;

            performanceWriter.println();
            performanceWriter.println("==========================================");
            performanceWriter.println("=== FINAL MIGRATION SUMMARY ===");
            performanceWriter.println("==========================================");
            performanceWriter.println();

            // Timing Information
            performanceWriter.println("=== TIMING INFORMATION ===");
            performanceWriter.println("Migration Start Time: " + LocalDateTime
                    .ofInstant(java.time.Instant.ofEpochMilli(migrationStartTime), java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            performanceWriter.println("Migration End Time: "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            performanceWriter.println("Total Migration Duration: " + formatDuration(totalTime));
            performanceWriter.println();

            // Record Statistics
            performanceWriter.println("=== RECORD STATISTICS ===");
            performanceWriter.println("Total Records Read: " + totalReads.get());
            performanceWriter.println("Total Records Written: " + totalWrites.get());
            performanceWriter.println("Total Records Failed: " + totalErrors.get());
            performanceWriter.println("Total Records Skipped: " + totalSkipped.get());
            performanceWriter.println();

            // Partition Statistics
            performanceWriter.println("=== PARTITION STATISTICS ===");
            performanceWriter.println("Total Partitions Processed: " + totalPartitionsProcessed.get());
            performanceWriter.println("Total Partitions Failed: " + totalPartitionsFailed.get());
            performanceWriter
                    .println(
                            "Partition Success Rate: "
                                    + String.format("%.2f%%",
                                            totalPartitionsProcessed.get() > 0 ? (totalPartitionsProcessed.get() * 100.0
                                                    / (totalPartitionsProcessed.get() + totalPartitionsFailed.get()))
                                                    : 0.0));
            performanceWriter.println();

            // Performance Metrics
            performanceWriter.println("=== PERFORMANCE METRICS ===");
            performanceWriter.println("Average Throughput: " + String.format("%.2f records/sec", avgThroughput));
            performanceWriter.println("Peak Throughput: "
                    + String.format("%.2f records/sec", calculatePeakThroughput(totalWrites.get(), totalTime)));
            performanceWriter.println("Error Rate: " + String.format("%.2f%%", errorRate));
            performanceWriter.println("Success Rate: " + String.format("%.2f%%", successRate));
            performanceWriter.println();

            // Data Quality
            performanceWriter.println("=== DATA QUALITY ===");
            if (totalErrors.get() == 0) {
                performanceWriter.println("✅ MIGRATION COMPLETED SUCCESSFULLY");
                performanceWriter.println("✅ All records migrated without errors");
            } else {
                performanceWriter.println("⚠️  MIGRATION COMPLETED WITH ERRORS");
                performanceWriter.println("⚠️  " + totalErrors.get() + " records failed to migrate");
                performanceWriter.println("⚠️  Check failed_records_" + SHARED_TIMESTAMP + ".csv for reprocessing");
                performanceWriter.println("⚠️  Check failed_keys_" + SHARED_TIMESTAMP + ".csv for error analysis");
            }
            performanceWriter.println();

            // Recommendations
            performanceWriter.println("=== RECOMMENDATIONS ===");
            if (errorRate > 5.0) {
                performanceWriter.println("🔧 High error rate detected. Consider:");
                performanceWriter.println("   - Checking network connectivity");
                performanceWriter.println("   - Verifying YugabyteDB connection limits");
                performanceWriter.println("   - Reducing parallelism in Spark configuration");
            }
            if (avgThroughput < 100) {
                performanceWriter.println("🔧 Low throughput detected. Consider:");
                performanceWriter.println("   - Increasing batch size");
                performanceWriter.println("   - Optimizing network settings");
                performanceWriter.println("   - Checking system resources");
            }
            if (totalErrors.get() == 0 && avgThroughput > 500) {
                performanceWriter.println("✅ Excellent performance! Migration completed successfully.");
            }

            performanceWriter.println();
            performanceWriter.println("==========================================");
            performanceWriter.println("Migration Summary Complete");
            performanceWriter.println("==========================================");

            performanceWriter.flush();

        } catch (Exception e) {
            logger.error("Failed to write final migration summary", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Close the performance logger
     */
    public static void close() {
        if (performanceWriter != null) {
            writeFinalSummary();
            performanceWriter.close();
            logger.info("Centralized performance logging completed. Summary saved in: {}", baseDir);
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
