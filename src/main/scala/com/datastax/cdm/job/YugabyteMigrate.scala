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
package com.datastax.cdm.job

import com.datastax.cdm.job.IJobSessionFactory.JobType
import com.datastax.cdm.properties.KnownProperties
import com.datastax.cdm.data.PKFactory.Side
import com.datastax.cdm.yugabyte.error.CentralizedPerformanceLogger

object YugabyteMigrate extends BasePartitionJob {
  jobType = JobType.MIGRATE
  setup("YugabyteDB Migrate Job", new YugabyteCopyJobSessionFactory())
  execute()
  finish()
  
  protected def execute(): Unit = {
    if (!parts.isEmpty()) {
      originConnection.withSessionDo(originSession => 
        jobFactory.getInstance(originSession, null, propertyHelper).initCdmRun(runId, prevRunId, parts, trackRunFeature, jobType))
      var ma = new CDMMetricsAccumulator(jobType)
      sContext.register(ma, "CDMMetricsAccumulator")
      
      val bcOriginConfig = sContext.broadcast(sContext.getConf)
      val bcConnectionFetcher = sContext.broadcast(connectionFetcher)
      val bcPropHelper = sContext.broadcast(propertyHelper)
      val bcJobFactory = sContext.broadcast(jobFactory)
      val bcKeyspaceTableValue = sContext.broadcast(keyspaceTableValue)
      val bcRunId = sContext.broadcast(runId)

      slices.foreach(slice => {
        if (null == originConnection) {
    		originConnection = bcConnectionFetcher.value.getConnection(bcOriginConfig.value, Side.ORIGIN, bcPropHelper.value.getString(KnownProperties.READ_CL), bcRunId.value)
            trackRunFeature = null // No track run for YugabyteDB target
        }
        originConnection.withSessionDo(originSession => {
            bcJobFactory.value.getInstance(originSession, null, bcPropHelper.value)
              .processPartitionRange(slice, trackRunFeature, bcRunId.value)
              ma.add(slice.getJobCounter())
        })
      })
      
      ma.value.printMetrics(runId, trackRunFeature);
      
      // Add configuration parameters to summary
      val configParams = buildConfigurationSummary();
      CentralizedPerformanceLogger.addConfigurationParameters(configParams);
      
      // Write final migration summary
      CentralizedPerformanceLogger.writeFinalSummary();
    }
  }
  
  override def finish(): Unit = {
    // Close centralized performance logger
    CentralizedPerformanceLogger.close();
    super.finish();
  }
  
  /**
   * Build configuration summary for performance tuning
   */
  private def buildConfigurationSummary(): String = {
    val sb = new StringBuilder();
    
    // Spark Configuration
    sb.append("Spark Configuration:\n");
    sb.append(s"  Master: ${sContext.getConf.get("spark.master", "local[*]")}\n");
    // In local mode, executor memory/cores might not be set in SparkConf
    // Check both SparkConf and system properties
    val driverMemory = sContext.getConf.getOption("spark.driver.memory").getOrElse(
      System.getProperty("spark.driver.memory", "1g")
    );
    val executorMemory = sContext.getConf.getOption("spark.executor.memory").getOrElse(
      System.getProperty("spark.executor.memory", 
        if (sContext.getConf.get("spark.master", "").startsWith("local")) driverMemory else "1g")
    );
    val executorCores = sContext.getConf.getOption("spark.executor.cores").getOrElse(
      System.getProperty("spark.executor.cores", "1")
    );
    sb.append(s"  Driver Memory: $driverMemory\n");
    sb.append(s"  Executor Memory: $executorMemory\n");
    sb.append(s"  Executor Cores: $executorCores\n");
    sb.append(s"  Executor Instances: ${sContext.getConf.get("spark.executor.instances", "1")}\n");
    sb.append(s"  Parallelism: ${sContext.getConf.get("spark.default.parallelism", "1")}\n");
    
    // CDM Configuration
    sb.append("\nCDM Configuration:\n");
    // Use correct property names from KnownProperties
    val originRateLimit = propertyHelper.getInteger(KnownProperties.PERF_RATELIMIT_ORIGIN);
    val targetRateLimit = propertyHelper.getInteger(KnownProperties.PERF_RATELIMIT_TARGET);
    val batchSize = propertyHelper.getNumber(KnownProperties.PERF_BATCH_SIZE);
    val fetchSize = propertyHelper.getNumber(KnownProperties.PERF_FETCH_SIZE);
    val numParts = propertyHelper.getNumber(KnownProperties.PERF_NUM_PARTS);
    val yugabyteBatchSize = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_BATCH_SIZE);
    
    sb.append(s"  Origin Rate Limit: ${if (originRateLimit != null) originRateLimit else "20000"} reads/sec\n");
    sb.append(s"  Target Rate Limit: ${if (targetRateLimit != null) targetRateLimit else "20000"} writes/sec\n");
    sb.append(s"  Batch Size: ${if (yugabyteBatchSize != null) yugabyteBatchSize else (if (batchSize != null) batchSize else "25")}\n");
    sb.append(s"  Fetch Size: ${if (fetchSize != null) fetchSize else "1000"}\n");
    sb.append(s"  Number of Partitions: ${if (numParts != null) numParts else "40"}\n");
    
    // Connection Configuration
    sb.append("\nConnection Configuration:\n");
    sb.append(s"  Origin Host: ${getPropertyOrDefault(KnownProperties.CONNECT_ORIGIN_HOST, "N/A")}\n");
    sb.append(s"  Origin Port: ${getPropertyOrDefault(KnownProperties.CONNECT_ORIGIN_PORT, "N/A")}\n");
    sb.append(s"  Target Host: ${getPropertyOrDefault(KnownProperties.TARGET_HOST, "N/A")}\n");
    sb.append(s"  Target Port: ${getPropertyOrDefault(KnownProperties.TARGET_PORT, "N/A")}\n");
    sb.append(s"  Target Database: ${getPropertyOrDefault(KnownProperties.TARGET_DATABASE, "N/A")}\n");
    
    // Performance Tuning Recommendations
    sb.append("\nPerformance Tuning Recommendations:\n");
    sb.append("  To increase throughput, consider:\n");
    sb.append("  - Increasing spark.executor.memory and spark.executor.cores\n");
    sb.append("  - Increasing spark.cdm.rate.target.writesPerSecond\n");
    sb.append("  - Increasing spark.cdm.batch.size and spark.cdm.fetch.size\n");
    sb.append("  - Adding more executor instances\n");
    sb.append("  - Optimizing network settings\n");
    
    sb.toString();
  }
  
  /**
   * Helper method to get property value with default
   */
  private def getPropertyOrDefault(propertyName: String, defaultValue: String): String = {
    val value = propertyHelper.getString(propertyName);
    if (value != null && !value.trim.isEmpty()) value else defaultValue;
  }
}

