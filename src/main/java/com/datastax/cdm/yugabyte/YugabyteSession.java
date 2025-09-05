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
            String host = propertyHelper.getString(KnownProperties.TARGET_HOST);
            String port = propertyHelper.getString(KnownProperties.TARGET_PORT);
            String database = propertyHelper.getString(KnownProperties.TARGET_DATABASE);
            String username = propertyHelper.getString(KnownProperties.TARGET_USERNAME);
            String password = propertyHelper.getString(KnownProperties.TARGET_PASSWORD);

            String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);

            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("ssl", "false"); // Adjust based on your setup

            logger.info("Connecting to YugabyteDB at: {}", url);
            return DriverManager.getConnection(url, props);

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
