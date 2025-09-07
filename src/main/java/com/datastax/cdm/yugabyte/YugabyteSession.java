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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.data.PKFactory;
import com.datastax.cdm.properties.IPropertyHelper;
import com.datastax.cdm.properties.KnownProperties;
import com.datastax.cdm.schema.YugabyteTable;
import com.datastax.cdm.yugabyte.statement.YugabyteUpsertStatement;

public class YugabyteSession {
    public Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    boolean logDebug = logger.isDebugEnabled();

    private final IPropertyHelper propertyHelper;
    private final Connection connection;
    private final YugabyteTable yugabyteTable;
    private final boolean isOrigin;
    private PKFactory pkFactory;

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
            // Debug: Check property keys
            logger.info("Checking YugabyteDB properties:");
            logger.info("  TARGET_HOST property key: {}", KnownProperties.TARGET_HOST);
            logger.info("  TARGET_PORT property key: {}", KnownProperties.TARGET_PORT);
            logger.info("  TARGET_DATABASE property key: {}", KnownProperties.TARGET_DATABASE);
            logger.info("  TARGET_USERNAME property key: {}", KnownProperties.TARGET_USERNAME);
            logger.info("  TARGET_PASSWORD property key: {}", KnownProperties.TARGET_PASSWORD);

            String host = propertyHelper.getString(KnownProperties.TARGET_HOST);
            // Port is defined as NUMBER type, so we need to get it as a number and convert to string
            Number portNumber = propertyHelper.getNumber(KnownProperties.TARGET_PORT);
            String port = (portNumber != null) ? portNumber.toString() : null;
            String database = propertyHelper.getString(KnownProperties.TARGET_DATABASE);
            String username = propertyHelper.getString(KnownProperties.TARGET_USERNAME);
            String password = propertyHelper.getString(KnownProperties.TARGET_PASSWORD);

            // Debug logging to help identify the issue
            logger.info("YugabyteDB Connection Parameters:");
            logger.info("  Host: {}", host);
            logger.info("  Port: {}", port);
            logger.info("  Database: {}", database);
            logger.info("  Username: {}", username);

            // Validate that port is not null or empty
            if (port == null || port.trim().isEmpty()) {
                throw new RuntimeException(
                        "YugabyteDB port is null or empty. Check your properties file for 'spark.cdm.connect.target.yugabyte.port'");
            }

            String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);

            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("ssl", "false"); // Adjust based on your setup

            // Connection pooling and retry settings to handle "too many clients" errors
            props.setProperty("maxConnections", "10"); // Limit concurrent connections per session
            props.setProperty("connectionTimeout", "30000"); // 30 seconds
            props.setProperty("socketTimeout", "60000"); // 60 seconds
            props.setProperty("loginTimeout", "30"); // 30 seconds
            props.setProperty("tcpKeepAlive", "true");
            props.setProperty("ApplicationName", "CassandraDataMigrator");

            // Connection retry logic
            int maxRetries = 5;
            int retryDelay = 2000; // 2 seconds
            Connection conn = null;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    logger.info("Connecting to YugabyteDB at: {} (attempt {}/{})", url, attempt, maxRetries);
                    conn = DriverManager.getConnection(url, props);
                    logger.info("Successfully connected to YugabyteDB on attempt {}", attempt);
                    break;
                } catch (SQLException e) {
                    if (e.getMessage().contains("too many clients already")) {
                        logger.warn("Connection attempt {} failed due to too many clients. Retrying in {}ms...",
                                attempt, retryDelay);
                        if (attempt < maxRetries) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(retryDelay);
                                retryDelay *= 2; // Exponential backoff
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Connection interrupted", ie);
                            }
                        } else {
                            logger.error("Failed to connect after {} attempts due to connection limit", maxRetries);
                            throw e;
                        }
                    } else {
                        logger.error("Failed to connect to YugabyteDB on attempt {}", attempt, e);
                        throw e;
                    }
                }
            }

            if (conn == null) {
                throw new RuntimeException("Failed to establish connection after all retry attempts");
            }

            return conn;

        } catch (SQLException e) {
            logger.error("Failed to connect to YugabyteDB", e);
            throw new RuntimeException("Failed to connect to YugabyteDB", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.error("Error closing YugabyteDB connection", e);
        }
    }
}
