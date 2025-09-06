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
package com.datastax.cdm.yugabyte.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.data.Record;
import com.datastax.cdm.properties.IPropertyHelper;
import com.datastax.cdm.schema.YugabyteTable;
import com.datastax.cdm.yugabyte.YugabyteSession;
import com.datastax.cdm.yugabyte.mapping.DataTypeMapper;
import com.datastax.oss.driver.api.core.cql.Row;

public class YugabyteUpsertStatement {
    private static final Logger logger = LoggerFactory.getLogger(YugabyteUpsertStatement.class);

    private final IPropertyHelper propertyHelper;
    private final YugabyteSession session;
    private final YugabyteTable yugabyteTable;
    private final DataTypeMapper dataTypeMapper;
    private final String upsertSQL;
    private final List<String> columnNames;
    private final List<Class<?>> bindClasses;

    public YugabyteUpsertStatement(IPropertyHelper propertyHelper, YugabyteSession session) {
        this.propertyHelper = propertyHelper;
        this.session = session;
        this.yugabyteTable = session.getYugabyteTable();
        this.dataTypeMapper = yugabyteTable.getDataTypeMapper();
        this.columnNames = yugabyteTable.getAllColumnNames();
        this.bindClasses = yugabyteTable.getBindClasses();

        this.upsertSQL = buildUpsertStatement();
        logger.info("YugabyteDB Upsert SQL: {}", upsertSQL);
    }

    private String buildUpsertStatement() {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(yugabyteTable.getKeyspaceTable()).append(" (");

        // Add column names
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0)
                sql.append(", ");
            sql.append(columnNames.get(i));
        }

        sql.append(") VALUES (");

        // Add placeholders
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0)
                sql.append(", ");
            sql.append("?");
        }

        sql.append(") ON CONFLICT (");

        // Add primary key columns for ON CONFLICT
        List<String> primaryKeys = yugabyteTable.getPrimaryKeyNames();
        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0)
                sql.append(", ");
            sql.append(primaryKeys.get(i));
        }

        sql.append(") DO UPDATE SET ");

        // Add non-primary key columns for UPDATE
        boolean first = true;
        for (String columnName : columnNames) {
            if (!primaryKeys.contains(columnName)) {
                if (!first)
                    sql.append(", ");
                sql.append(columnName).append(" = EXCLUDED.").append(columnName);
                first = false;
            }
        }

        return sql.toString();
    }

    public void execute(Record record) throws SQLException {
        if (record == null) {
            throw new RuntimeException("Record is null");
        }

        Row originRow = record.getOriginRow();
        if (originRow == null) {
            throw new RuntimeException("Origin row is null");
        }

        try (PreparedStatement statement = session.getConnection().prepareStatement(upsertSQL)) {

            // Bind values from origin row to YugabyteDB statement
            for (int i = 0; i < columnNames.size(); i++) {
                String columnName = columnNames.get(i);
                Class<?> bindClass = bindClasses.get(i);

                // Get value from origin row (assuming column names match)
                Object value = getValueFromOriginRow(originRow, columnName);

                // Convert value to appropriate type
                Object convertedValue = dataTypeMapper.convertValue(value, null, bindClass);

                // Set parameter
                statement.setObject(i + 1, convertedValue);

                if (logger.isDebugEnabled()) {
                    logger.debug("Binding column {}: {} -> {}", columnName, value, convertedValue);
                }
            }

            // Execute the statement
            int rowsAffected = statement.executeUpdate();
            logger.debug("Upserted {} rows", rowsAffected);

        } catch (SQLException e) {
            logger.error("Error executing YugabyteDB upsert for record: {}", record, e);
            throw e;
        }
    }

    private Object getValueFromOriginRow(Row originRow, String columnName) {
        try {
            // Try to get value by column name
            return originRow.getObject(columnName);
        } catch (Exception e) {
            // If that fails, try to get by index
            try {
                int index = columnNames.indexOf(columnName);
                if (index >= 0) {
                    return originRow.getObject(index);
                }
            } catch (Exception e2) {
                logger.warn("Could not get value for column {} from origin row", columnName);
            }
            return null;
        }
    }

    public String getSQL() {
        return upsertSQL;
    }
}
