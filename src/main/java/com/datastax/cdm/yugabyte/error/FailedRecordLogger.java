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
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.data.Record;
import com.datastax.oss.driver.api.core.cql.Row;

/**
 * Handles logging of failed records to separate files for analysis and reprocessing
 */
public class FailedRecordLogger {
    private static final Logger logger = LoggerFactory.getLogger(FailedRecordLogger.class);

    // Shared timestamp across all instances to avoid multiple log files
    private static final String SHARED_TIMESTAMP = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

    private final ReentrantLock lock = new ReentrantLock();
    private final String baseDir;
    private final String timestamp;

    private PrintWriter failedRecordsWriter;
    private PrintWriter failedKeysWriter;
    private PrintWriter performanceWriter;

    private long startTime;
    private long totalReads = 0;
    private long totalWrites = 0;
    private long totalErrors = 0;
    private long totalSkipped = 0;

    public FailedRecordLogger(String baseDir) {
        this.baseDir = baseDir != null ? baseDir : "migration_logs";
        this.timestamp = SHARED_TIMESTAMP; // Use shared timestamp to avoid multiple files
        this.startTime = System.currentTimeMillis();

        initializeFiles();
    }

    private void initializeFiles() {
        try {
            // Create directory if it doesn't exist
            Path dir = Paths.get(baseDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            // Initialize failed records file (complete record data)
            String failedRecordsFile = String.format("%s/failed_records_%s.csv", baseDir, timestamp);
            failedRecordsWriter = new PrintWriter(new FileWriter(failedRecordsFile, true));
            failedRecordsWriter.println("timestamp,primary_key,record_data");

            // Initialize failed keys file (keys and reasons)
            String failedKeysFile = String.format("%s/failed_keys_%s.csv", baseDir, timestamp);
            failedKeysWriter = new PrintWriter(new FileWriter(failedKeysFile, true));
            failedKeysWriter.println("timestamp,primary_key,error_type,error_message");

            // Initialize performance file
            String performanceFile = String.format("%s/performance_%s.txt", baseDir, timestamp);
            performanceWriter = new PrintWriter(new FileWriter(performanceFile, true));
            performanceWriter.println("=== Migration Performance Report ===");
            performanceWriter.println(
                    "Start Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            performanceWriter.println();

            logger.info("Failed record logging initialized:");
            logger.info("  Failed Records: {}", failedRecordsFile);
            logger.info("  Failed Keys: {}", failedKeysFile);
            logger.info("  Performance: {}", performanceFile);

        } catch (IOException e) {
            logger.error("Failed to initialize error logging files", e);
            throw new RuntimeException("Could not initialize error logging", e);
        }
    }

    /**
     * Log a failed record with complete data for reprocessing (clean data only, no error details)
     */
    public void logFailedRecord(Record record, Exception error) {
        lock.lock();
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            String primaryKey = record.getPk() != null ? record.getPk().toString() : "null";
            String recordData = serializeRecord(record);
            // Only log timestamp, primary key, and record data - no error message for clean reprocessing
            failedRecordsWriter.printf("%s,\"%s\",\"%s\"%n", timestamp, primaryKey, recordData);
            failedRecordsWriter.flush();

            totalErrors++;

        } catch (Exception e) {
            logger.error("Failed to log failed record", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Log failed record key and reason for analysis
     */
    public void logFailedKey(Record record, Exception error) {
        lock.lock();
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            String primaryKey = record.getPk() != null ? record.getPk().toString() : "null";
            String errorType = error.getClass().getSimpleName();
            String errorMessage = error.getMessage() != null ? error.getMessage().replace("\n", " ").replace("\r", " ")
                    : "Unknown error";

            failedKeysWriter.printf("%s,\"%s\",\"%s\",\"%s\"%n", timestamp, primaryKey, errorType, errorMessage);
            failedKeysWriter.flush();

        } catch (Exception e) {
            logger.error("Failed to log failed key", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Update performance metrics
     */
    public void updateMetrics(long reads, long writes, long errors, long skipped) {
        lock.lock();
        try {
            this.totalReads += reads;
            this.totalWrites += writes;
            this.totalErrors += errors;
            this.totalSkipped += skipped;

            // Note: CentralizedPerformanceLogger is updated directly from YugabyteCopyJobSession
            // to avoid double counting

        } catch (Exception e) {
            logger.error("Failed to update performance metrics", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Write final performance summary
     */
    public void writeFinalPerformanceSummary() {
        lock.lock();
        try {
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            double throughput = totalWrites > 0 ? (totalWrites * 1000.0 / totalTime) : 0.0;
            double errorRate = totalReads > 0 ? (totalErrors * 100.0 / totalReads) : 0.0;

            performanceWriter.println();
            performanceWriter.println("=== Final Performance Summary ===");
            performanceWriter.println(
                    "End Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            performanceWriter.println("Total Duration: " + formatDuration(totalTime));
            performanceWriter.println("Total Records Read: " + totalReads);
            performanceWriter.println("Total Records Written: " + totalWrites);
            performanceWriter.println("Total Records Failed: " + totalErrors);
            performanceWriter.println("Total Records Skipped: " + totalSkipped);
            performanceWriter.println("Average Throughput: " + String.format("%.2f records/sec", throughput));
            performanceWriter.println("Error Rate: " + String.format("%.2f%%", errorRate));
            performanceWriter.println("Success Rate: " + String.format("%.2f%%", 100.0 - errorRate));

            if (totalErrors > 0) {
                performanceWriter.println();
                performanceWriter.println("=== Error Analysis ===");
                performanceWriter.println("Failed records have been logged to:");
                performanceWriter.println(
                        "  - failed_records_" + timestamp + ".csv (clean record data for direct reprocessing)");
                performanceWriter
                        .println("  - failed_keys_" + timestamp + ".csv (keys and failure reasons for analysis)");
            }

            performanceWriter.flush();

        } catch (Exception e) {
            logger.error("Failed to write final performance summary", e);
        } finally {
            lock.unlock();
        }
    }

    private String serializeRecord(Record record) {
        try {
            StringBuilder sb = new StringBuilder();
            Row originRow = record.getOriginRow();
            if (originRow != null) {
                // Get all column values
                for (int i = 0; i < originRow.getColumnDefinitions().size(); i++) {
                    if (i > 0)
                        sb.append("|");
                    Object value = originRow.getObject(i);
                    sb.append(value != null ? value.toString() : "null");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error serializing record: " + e.getMessage();
        }
    }

    private String formatDuration(long milliseconds) {
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

    public void close() {
        lock.lock();
        try {
            writeFinalPerformanceSummary();

            if (failedRecordsWriter != null) {
                failedRecordsWriter.close();
            }
            if (failedKeysWriter != null) {
                failedKeysWriter.close();
            }
            if (performanceWriter != null) {
                performanceWriter.close();
            }

            logger.info("Failed record logging completed. Files saved in: {}", baseDir);

        } catch (Exception e) {
            logger.error("Error closing failed record logger", e);
        } finally {
            lock.unlock();
        }
    }
}
