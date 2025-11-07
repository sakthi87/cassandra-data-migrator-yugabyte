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
import java.sql.SQLException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.data.PKFactory;
import com.datastax.cdm.properties.IPropertyHelper;
import com.datastax.cdm.properties.KnownProperties;
import com.datastax.cdm.schema.YugabyteTable;
import com.datastax.cdm.yugabyte.statement.YugabyteUpsertStatement;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class YugabyteSession {
    public Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    boolean logDebug = logger.isDebugEnabled();

    private final IPropertyHelper propertyHelper;
    private final Connection connection;
    private final YugabyteTable yugabyteTable;
    private final boolean isOrigin;
    private PKFactory pkFactory;
    private HikariDataSource dataSource; // Keep reference for proper cleanup

    public YugabyteSession(IPropertyHelper propertyHelper, boolean isOrigin) {
        this.propertyHelper = propertyHelper;
        this.isOrigin = isOrigin;

        // Initialize YugabyteDB connection
        this.connection = initConnection(propertyHelper);

        // Initialize table metadata
        this.yugabyteTable = new YugabyteTable(propertyHelper, isOrigin, connection);
    }

    public void setPKFactory(PKFactory pkFactory) {
        this.pkFactory = pkFactory;
    }

    public PKFactory getPKFactory() {
        return this.pkFactory;
    }

    public Connection getConnection() {
        return connection;
    }

    public YugabyteTable getYugabyteTable() {
        return yugabyteTable;
    }

    public YugabyteUpsertStatement getYugabyteUpsertStatement() {
        if (isOrigin)
            throw new RuntimeException("This is not a target session");
        return new YugabyteUpsertStatement(propertyHelper, this);
    }

    private Connection initConnection(IPropertyHelper propertyHelper) {
        try {
            logger.info("Initializing YugabyteDB connection with HikariCP and YBClusterAwareDataSource");

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

            // Configure YBClusterAwareDataSource with HikariCP as per YugabyteDB documentation
            // Reference: https://docs.yugabyte.com/preview/develop/drivers-orms/java/yugabyte-jdbc-reference/
            Properties poolProperties = new Properties();
            poolProperties.setProperty("dataSourceClassName", "com.yugabyte.ysql.YBClusterAwareDataSource");
            
            // Basic connection properties
            poolProperties.setProperty("dataSource.serverName", host);
            poolProperties.setProperty("dataSource.portNumber", port);
            poolProperties.setProperty("dataSource.databaseName", database);
            poolProperties.setProperty("dataSource.user", username);
            poolProperties.setProperty("dataSource.password", password);

            // Additional endpoints for load balancing (optional)
            String additionalEndpoints = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_ADDITIONAL_ENDPOINTS);
            if (additionalEndpoints != null && !additionalEndpoints.trim().isEmpty()) {
                poolProperties.setProperty("dataSource.additionalEndpoints", additionalEndpoints);
                logger.info("  Additional Endpoints: {}", additionalEndpoints);
            }

            // Topology keys for geo-location aware load balancing (optional)
            String topologyKeys = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_TOPOLOGY_KEYS);
            if (topologyKeys != null && !topologyKeys.trim().isEmpty()) {
                poolProperties.setProperty("dataSource.topologyKeys", topologyKeys);
                logger.info("  Topology Keys: {}", topologyKeys);
            }

            // SSL configuration
            String sslEnabled = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SSL_ENABLED);
            String sslMode = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SSLMODE);
            String sslRootCert = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SSLROOTCERT);
            
            if (sslEnabled != null && "true".equalsIgnoreCase(sslEnabled)) {
                poolProperties.setProperty("dataSource.ssl", "true");
                if (sslMode != null && !sslMode.isEmpty()) {
                    poolProperties.setProperty("dataSource.sslmode", sslMode);
                } else {
                    poolProperties.setProperty("dataSource.sslmode", "require");
                }
                if (sslRootCert != null && !sslRootCert.isEmpty()) {
                    poolProperties.setProperty("dataSource.sslrootcert", sslRootCert);
                }
                logger.info("  SSL enabled with sslmode: {}", poolProperties.getProperty("dataSource.sslmode"));
            } else {
                poolProperties.setProperty("dataSource.ssl", "false");
                poolProperties.setProperty("dataSource.sslmode", "disable");
                logger.info("  SSL disabled");
            }

            // HikariCP pool configuration
            Number maxPoolSize = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_POOL_MAX_SIZE);
            Number minPoolSize = propertyHelper.getNumber(KnownProperties.TARGET_YUGABYTE_POOL_MIN_SIZE);
            
            poolProperties.setProperty("maximumPoolSize", 
                (maxPoolSize != null) ? maxPoolSize.toString() : "10");
            poolProperties.setProperty("minimumIdle", 
                (minPoolSize != null) ? minPoolSize.toString() : "2");
            
            // Connection timeout settings
            poolProperties.setProperty("connectionTimeout", "60000"); // 60 seconds
            poolProperties.setProperty("idleTimeout", "300000"); // 5 minutes
            poolProperties.setProperty("maxLifetime", "1800000"); // 30 minutes
            poolProperties.setProperty("leakDetectionThreshold", "60000"); // 60 seconds
            
            // Pool name for monitoring
            poolProperties.setProperty("poolName", "CDM-YugabyteDB-Pool");

            logger.info("HikariCP Pool Configuration:");
            logger.info("  Maximum Pool Size: {}", poolProperties.getProperty("maximumPoolSize"));
            logger.info("  Minimum Idle: {}", poolProperties.getProperty("minimumIdle"));
            logger.info("  Connection Timeout: {}ms", poolProperties.getProperty("connectionTimeout"));

            // Create HikariConfig and validate
            HikariConfig config = new HikariConfig(poolProperties);
            config.validate();

            // Create HikariDataSource with YBClusterAwareDataSource
            this.dataSource = new HikariDataSource(config);
            
            logger.info("Successfully created HikariDataSource with YBClusterAwareDataSource");

            // Get connection from pool
            Connection conn = dataSource.getConnection();
            logger.info("Successfully obtained connection from HikariCP pool");

            return conn;

        } catch (SQLException e) {
            logger.error("Failed to connect to YugabyteDB", e);
            throw new RuntimeException("Failed to connect to YugabyteDB", e);
        } catch (Exception e) {
            logger.error("Failed to initialize YugabyteDB connection pool", e);
            throw new RuntimeException("Failed to initialize YugabyteDB connection pool", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Closed YugabyteDB connection");
            }
            // Close HikariCP data source to properly cleanup the connection pool
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                logger.info("Closed HikariCP data source and connection pool");
            }
        } catch (SQLException e) {
            logger.error("Error closing YugabyteDB connection", e);
        }
    }
}
