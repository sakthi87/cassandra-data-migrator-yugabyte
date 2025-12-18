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
package com.datastax.cdm.yugabyte;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.data.PKFactory;
import com.datastax.cdm.properties.IPropertyHelper;
import com.datastax.cdm.properties.KnownProperties;
import com.datastax.cdm.schema.YugabyteTable;
import com.datastax.cdm.yugabyte.statement.YugabyteUpsertStatement;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.DriverDataSource;

/**
 * YugabyteSession manages connections to YugabyteDB using the YugabyteDB Smart Driver (YBClusterAwareDataSource) with
 * HikariCP connection pooling.
 *
 * Performance Optimizations (Phase 1+2): - Connection pooling with HikariCP for better connection reuse - YugabyteDB
 * Smart Driver with load balancing enabled - rewriteBatchedInserts=true for optimized batch INSERT performance -
 * Configurable prepareThreshold for server-side prepared statements - TCP keepalive and socket timeouts for reliable
 * connections
 */
public class YugabyteSession {
    public Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    boolean logDebug = logger.isDebugEnabled();

    private final IPropertyHelper propertyHelper;
    private final YugabyteTable yugabyteTable;
    private final boolean isOrigin;
    private PKFactory pkFactory;
    private HikariDataSource dataSource; // Connection pool - use this for getting connections!

    public YugabyteSession(IPropertyHelper propertyHelper, boolean isOrigin) {
        this.propertyHelper = propertyHelper;
        this.isOrigin = isOrigin;

        // Initialize YugabyteDB connection pool
        this.dataSource = initConnectionPool(propertyHelper);

        // Initialize table metadata using a connection from the pool
        try (Connection metadataConn = dataSource.getConnection()) {
            this.yugabyteTable = new YugabyteTable(propertyHelper, isOrigin, metadataConn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize YugabyteDB table metadata", e);
        }
    }

    public void setPKFactory(PKFactory pkFactory) {
        this.pkFactory = pkFactory;
    }

    public PKFactory getPKFactory() {
        return this.pkFactory;
    }

    /**
     * Get a connection from the pool. Caller MUST close the connection after use to return it to the pool.
     *
     * For batch operations, use getDataSource() instead and manage connections directly.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Get the HikariCP data source for direct pool access. Use this for batch operations that need to manage
     * connections themselves.
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public YugabyteTable getYugabyteTable() {
        return yugabyteTable;
    }

    /**
     * Get the configured batch size for YugabyteDB operations.
     */
    public int getBatchSize() {
        Number batchSize = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_BATCH_SIZE);
        return (batchSize != null) ? batchSize.intValue() : 25;
    }

    public YugabyteUpsertStatement getYugabyteUpsertStatement() {
        if (isOrigin)
            throw new RuntimeException("This is not a target session");
        return new YugabyteUpsertStatement(propertyHelper, this);
    }

    /**
     * Initialize the HikariCP connection pool with YugabyteDB Smart Driver.
     *
     * Key Performance Properties: - rewriteBatchedInserts=true: Rewrites batch INSERTs into multi-row INSERT statements
     * This is CRITICAL for batch performance - can improve batch INSERT speed by 10-50x - load_balance=true: Enables
     * cluster-aware load balancing across YugabyteDB nodes - prepareThreshold: Number of times a statement must be
     * executed before using server-side prep - tcpKeepAlive=true: Maintains persistent connections
     */
    private HikariDataSource initConnectionPool(IPropertyHelper propertyHelper) {
        try {
            logger.info("Initializing YugabyteDB connection pool with HikariCP and YBClusterAwareDataSource");
            logger.info("=========================================================================");
            logger.info("PERFORMANCE OPTIMIZATIONS ENABLED:");
            logger.info("  - PreparedStatement reuse (Phase 1)");
            logger.info("  - JDBC Batching with rewriteBatchedInserts (Phase 2)");
            logger.info("  - Connection pooling for parallelism");
            logger.info("  - Smart Driver load balancing");
            logger.info("=========================================================================");

            String host = propertyHelper.getString(KnownProperties.TARGET_HOST);
            Number portNumber = propertyHelper.getNumber(KnownProperties.TARGET_PORT);
            String port = (portNumber != null) ? portNumber.toString() : null;
            String database = propertyHelper.getString(KnownProperties.TARGET_DATABASE);
            String username = propertyHelper.getString(KnownProperties.TARGET_USERNAME);
            String password = propertyHelper.getString(KnownProperties.TARGET_PASSWORD);

            // Validate required parameters
            if (port == null || port.trim().isEmpty()) {
                throw new RuntimeException(
                        "YugabyteDB port is null or empty. Check your properties file for 'spark.cdm.connect.target.yugabyte.port'");
            }

            logger.info("YugabyteDB Connection Parameters:");
            logger.info("  Host: {}", host);
            logger.info("  Port: {}", port);
            logger.info("  Database: {}", database);
            logger.info("  Username: {}", username);

            // Configure YugabyteDB connection with HikariCP
            // Use JDBC URL approach for better compatibility (works with both Smart Driver and standard driver)
            Properties poolProperties = new Properties();

            // Build JDBC URL for YugabyteDB
            // Format: jdbc:yugabytedb://host:port/database?param1=value1&param2=value2
            StringBuilder jdbcUrl = new StringBuilder();
            jdbcUrl.append("jdbc:yugabytedb://").append(host).append(":").append(port).append("/").append(database);

            // Add connection parameters as URL parameters
            List<String> urlParams = new ArrayList<>();
            urlParams.add("user=" + username);
            urlParams.add("password=" + password);

            // ========================================================================
            // CRITICAL PERFORMANCE PROPERTIES FOR YUGABYTEDB
            // ========================================================================

            // 1. rewriteBatchedInserts - CRITICAL for batch INSERT performance!
            // When true, rewrites batch INSERTs like:
            // INSERT INTO t VALUES (1); INSERT INTO t VALUES (2); INSERT INTO t VALUES (3);
            // Into:
            // INSERT INTO t VALUES (1), (2), (3);
            // This reduces network round-trips dramatically (10-50x improvement)
            Boolean rewriteBatched = propertyHelper.getBoolean(KnownProperties.TARGET_YUGABYTE_REWRITE_BATCHED_INSERTS);
            if (rewriteBatched == null || rewriteBatched) {
                urlParams.add("rewriteBatchedInserts=true");
            }
            logger.info("  rewriteBatchedInserts: {} (CRITICAL for batch performance)",
                    (rewriteBatched == null || rewriteBatched) ? "true" : "false");

            // 2. Load balancing - distribute connections across YugabyteDB nodes
            // Only enable if topologyKeys are provided (required by Smart Driver)
            String topologyKeys = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_TOPOLOGY_KEYS);
            Boolean loadBalance = propertyHelper.getBoolean(KnownProperties.TARGET_YUGABYTE_LOAD_BALANCE);
            if (loadBalance != null && loadBalance && topologyKeys != null && !topologyKeys.trim().isEmpty()) {
                urlParams.add("loadBalance=true");
                urlParams.add("topologyKeys=" + topologyKeys);
                logger.info("  loadBalance: true (Smart Driver load balancing enabled)");
                logger.info("  Topology Keys: {}", topologyKeys);
            } else if (loadBalance != null && loadBalance) {
                logger.warn(
                        "  loadBalance requested but topologyKeys not provided - disabling loadBalance for local/single-node setup");
            }

            // 3. prepareThreshold - use server-side prepared statements after N executions
            // Lower value = earlier use of server-side prep = better performance for repeated queries
            Number prepareThreshold = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_PREPARE_THRESHOLD);
            String prepThresholdValue = (prepareThreshold != null) ? prepareThreshold.toString() : "5";
            urlParams.add("prepareThreshold=" + prepThresholdValue);
            logger.info("  prepareThreshold: {} (server-side prepared statements)", prepThresholdValue);

            // 4. TCP KeepAlive - maintain persistent connections
            Boolean tcpKeepAlive = propertyHelper.getBoolean(KnownProperties.TARGET_YUGABYTE_TCP_KEEPALIVE);
            if (tcpKeepAlive == null || tcpKeepAlive) {
                urlParams.add("tcpKeepAlive=true");
            }
            logger.info("  tcpKeepAlive: {}", (tcpKeepAlive == null || tcpKeepAlive) ? "true" : "false");

            // 5. Socket timeout
            Number socketTimeout = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_SOCKET_TIMEOUT);
            String socketTimeoutValue = (socketTimeout != null) ? socketTimeout.toString() : "60000";
            urlParams.add("socketTimeout=" + socketTimeoutValue);
            logger.info("  socketTimeout: {}ms", socketTimeoutValue);

            // ========================================================================
            // ADDITIONAL ENDPOINTS AND TOPOLOGY (for distributed clusters)
            // ========================================================================

            // Additional endpoints for load balancing
            String additionalEndpoints = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_ADDITIONAL_ENDPOINTS);
            if (additionalEndpoints != null && !additionalEndpoints.trim().isEmpty()) {
                urlParams.add("additionalEndpoints=" + additionalEndpoints);
                logger.info("  Additional Endpoints: {}", additionalEndpoints);
            }

            // ========================================================================
            // SSL CONFIGURATION
            // ========================================================================

            String sslEnabled = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SSL_ENABLED);
            String sslMode = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SSLMODE);
            String sslRootCert = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SSLROOTCERT);

            if (sslEnabled != null && "true".equalsIgnoreCase(sslEnabled)) {
                urlParams.add("ssl=true");
                if (sslMode != null && !sslMode.isEmpty()) {
                    urlParams.add("sslmode=" + sslMode);
                } else {
                    urlParams.add("sslmode=require");
                }
                if (sslRootCert != null && !sslRootCert.isEmpty()) {
                    urlParams.add("sslrootcert=" + sslRootCert);
                }
                logger.info("  SSL enabled with sslmode: {}",
                        (sslMode != null && !sslMode.isEmpty()) ? sslMode : "require");
            } else {
                urlParams.add("ssl=false");
                urlParams.add("sslmode=disable");
                logger.info("  SSL disabled");
            }

            // Build final JDBC URL with all parameters
            if (!urlParams.isEmpty()) {
                jdbcUrl.append("?").append(String.join("&", urlParams));
            }

            String finalJdbcUrl = jdbcUrl.toString();
            logger.info("JDBC URL: jdbc:yugabytedb://{}:{}/{} (parameters hidden)", host, port, database);

            // Set JDBC URL in HikariCP properties
            // Note: We'll load the driver class explicitly before creating HikariConfig
            poolProperties.setProperty("jdbcUrl", finalJdbcUrl);

            // ========================================================================
            // HIKARICP POOL CONFIGURATION
            // ========================================================================

            Number maxPoolSize = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_POOL_MAX_SIZE);
            Number minPoolSize = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_POOL_MIN_SIZE);

            // Increased defaults for better parallelism
            poolProperties.setProperty("maximumPoolSize", (maxPoolSize != null) ? maxPoolSize.toString() : "20");
            poolProperties.setProperty("minimumIdle", (minPoolSize != null) ? minPoolSize.toString() : "5");

            // Connection timeout settings - optimized for bulk loading
            poolProperties.setProperty("connectionTimeout", "60000"); // 60 seconds
            poolProperties.setProperty("idleTimeout", "300000"); // 5 minutes
            poolProperties.setProperty("maxLifetime", "1800000"); // 30 minutes
            poolProperties.setProperty("leakDetectionThreshold", "120000"); // 2 minutes (increased for batch ops)

            // Pool name for monitoring
            poolProperties.setProperty("poolName", "CDM-YugabyteDB-HighPerf-Pool");

            logger.info("HikariCP Pool Configuration:");
            logger.info("  Maximum Pool Size: {}", poolProperties.getProperty("maximumPoolSize"));
            logger.info("  Minimum Idle: {}", poolProperties.getProperty("minimumIdle"));
            logger.info("  Connection Timeout: {}ms", poolProperties.getProperty("connectionTimeout"));
            logger.info("  Leak Detection: {}ms", poolProperties.getProperty("leakDetectionThreshold"));

            // Batch size configuration
            Number batchSize = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_BATCH_SIZE);
            logger.info("Batch Configuration:");
            logger.info("  Batch Size: {} records per batch", (batchSize != null) ? batchSize : 25);

            // Explicitly load and register the YugabyteDB driver class
            // Based on: https://www.yugabyte.com/blog/automatic-failover-jdbc-smart-driver-hikari/
            // The driver must be loaded and registered before HikariCP can use it
            Driver yugabyteDriver = null;
            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) {
                    classLoader = YugabyteSession.class.getClassLoader();
                }
                Class<?> driverClass = Class.forName("com.yugabyte.Driver", true, classLoader);
                yugabyteDriver = (Driver) driverClass.getDeclaredConstructor().newInstance();
                DriverManager.registerDriver(yugabyteDriver);

                // Test that the driver accepts the JDBC URL (required for HikariCP's DriverDataSource)
                if (!yugabyteDriver.acceptsURL(finalJdbcUrl)) {
                    logger.warn("Driver does not accept URL, but continuing - this may cause issues");
                }

                // Test a connection to ensure driver works (then close it immediately)
                try (Connection testConn = yugabyteDriver.connect(finalJdbcUrl, new Properties())) {
                    if (testConn != null) {
                        logger.info("Successfully tested YugabyteDB driver connection");
                        testConn.close();
                    }
                } catch (SQLException e) {
                    logger.warn("Test connection failed, but driver is registered: {}", e.getMessage());
                }

                logger.info("Successfully loaded and registered YugabyteDB driver: com.yugabyte.Driver");
            } catch (Exception e) {
                logger.error("Failed to load/register YugabyteDB driver class: com.yugabyte.Driver", e);
                throw new RuntimeException(
                        "YugabyteDB driver not found or could not be registered. Ensure jdbc-yugabytedb dependency is included.",
                        e);
            }

            // Create HikariConfig and validate
            // Reference: https://www.yugabyte.com/blog/automatic-failover-jdbc-smart-driver-hikari/
            // Since DriverManager.getDriver() has classloader issues in Spark, we'll use a custom DataSource
            // that wraps our already-loaded driver instance
            HikariConfig config = new HikariConfig();
            // Set JDBC URL
            config.setJdbcUrl(finalJdbcUrl);
            // Copy pool properties
            config.setMaximumPoolSize(Integer.parseInt(poolProperties.getProperty("maximumPoolSize", "20")));
            config.setMinimumIdle(Integer.parseInt(poolProperties.getProperty("minimumIdle", "5")));
            config.setConnectionTimeout(Long.parseLong(poolProperties.getProperty("connectionTimeout", "60000")));
            config.setIdleTimeout(Long.parseLong(poolProperties.getProperty("idleTimeout", "300000")));
            config.setMaxLifetime(Long.parseLong(poolProperties.getProperty("maxLifetime", "1800000")));
            config.setLeakDetectionThreshold(
                    Long.parseLong(poolProperties.getProperty("leakDetectionThreshold", "120000")));
            config.setPoolName(poolProperties.getProperty("poolName", "CDM-YugabyteDB-HighPerf-Pool"));

            // Use a simple DataSource wrapper that uses our pre-loaded driver
            // This avoids DriverManager.getDriver() classloader issues in Spark
            final Properties driverProps = new Properties();
            driverProps.setProperty("user", username);
            driverProps.setProperty("password", password);
            final Driver finalDriver = yugabyteDriver;
            final String finalUrl = finalJdbcUrl;

            // Create a simple DataSource implementation using our driver
            DataSource customDataSource = new DataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    return finalDriver.connect(finalUrl, driverProps);
                }

                @Override
                public Connection getConnection(String user, String password) throws SQLException {
                    Properties props = new Properties(driverProps);
                    props.setProperty("user", user);
                    props.setProperty("password", password);
                    return finalDriver.connect(finalUrl, props);
                }

                @Override
                public java.io.PrintWriter getLogWriter() throws SQLException {
                    return null;
                }

                @Override
                public void setLogWriter(java.io.PrintWriter out) throws SQLException {
                }

                @Override
                public void setLoginTimeout(int seconds) throws SQLException {
                }

                @Override
                public int getLoginTimeout() throws SQLException {
                    return 0;
                }

                @Override
                public java.util.logging.Logger getParentLogger() {
                    return null;
                }

                @Override
                public <T> T unwrap(Class<T> iface) throws SQLException {
                    return null;
                }

                @Override
                public boolean isWrapperFor(Class<?> iface) throws SQLException {
                    return false;
                }
            };

            config.setDataSource(customDataSource);
            config.validate();

            // Create HikariDataSource
            HikariDataSource ds = new HikariDataSource(config);

            logger.info("=========================================================================");
            logger.info("Successfully created HikariDataSource with YugabyteDB JDBC Driver");
            logger.info("Connection pool ready for high-performance batch operations");
            logger.info("=========================================================================");

            return ds;

        } catch (Exception e) {
            logger.error("Failed to initialize YugabyteDB connection pool", e);
            throw new RuntimeException("Failed to initialize YugabyteDB connection pool", e);
        }
    }

    public void close() {
        // Close HikariCP data source to properly cleanup the connection pool
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Closed HikariCP data source and connection pool");
        }
    }
}
