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
package com.datastax.cdm.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.cdm.data.CqlConversion;
import com.datastax.cdm.properties.IPropertyHelper;
import com.datastax.cdm.properties.KnownProperties;
import com.datastax.cdm.properties.PropertyHelper;
import com.datastax.cdm.yugabyte.mapping.DataTypeMapper;
import com.datastax.oss.driver.api.core.type.DataType;

public class YugabyteTable extends BaseTable {
    private static final Logger logger = LoggerFactory.getLogger(YugabyteTable.class);

    private final Connection connection;
    private final Map<String, String> columnNameToPostgresTypeMap = new HashMap<>();
    private final List<String> primaryKeyNames = new ArrayList<>();
    private final List<String> allColumnNames = new ArrayList<>();
    private final List<Class<?>> bindClasses = new ArrayList<>();
    private final DataTypeMapper dataTypeMapper;

    public YugabyteTable(IPropertyHelper propertyHelper, boolean isOrigin, Connection connection) {
        super(propertyHelper, isOrigin);
        this.connection = connection;
        this.dataTypeMapper = new DataTypeMapper();

        // Discover table schema from YugabyteDB
        discoverTableSchema();
    }

    private void discoverTableSchema() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String[] tableParts = getKeyspaceTable().split("\\.");
            // In YugabyteDB, the database name is in the connection URL, not the schema
            // The schema is always "public" by default (PostgreSQL convention)
            // So we extract just the table name from keyspaceTable (which may be "db.table" or just "table")
            String schema = "public"; // YugabyteDB uses PostgreSQL schema convention
            String tableName = tableParts.length > 1 ? tableParts[tableParts.length - 1] : tableParts[0];

            logger.info("Discovering schema for table: {}.{} (database: {})", schema, tableName,
                    tableParts.length > 1 ? tableParts[0] : "current");

            // Get column information
            try (ResultSet columns = metaData.getColumns(null, schema, tableName, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String dataType = columns.getString("TYPE_NAME");
                    int dataTypeCode = columns.getInt("DATA_TYPE");

                    allColumnNames.add(columnName);
                    columnNameToPostgresTypeMap.put(columnName, dataType);

                    // Map PostgreSQL type to Java class
                    Class<?> bindClass = dataTypeMapper.getJavaClass(dataTypeCode, dataType);
                    bindClasses.add(bindClass);

                    logger.debug("Column: {} -> Type: {} -> Java Class: {}", columnName, dataType,
                            bindClass.getSimpleName());
                }
            }

            // Get primary key information
            try (ResultSet primaryKeys = metaData.getPrimaryKeys(null, schema, tableName)) {
                while (primaryKeys.next()) {
                    String columnName = primaryKeys.getString("COLUMN_NAME");
                    primaryKeyNames.add(columnName);
                    logger.debug("Primary Key: {}", columnName);
                }
            }

            // Set column names if not already set
            if (this.columnNames == null || this.columnNames.isEmpty()) {
                this.columnNames = new ArrayList<>(allColumnNames);
            }

            logger.info("Discovered {} columns and {} primary key columns", allColumnNames.size(),
                    primaryKeyNames.size());

        } catch (SQLException e) {
            logger.error("Failed to discover table schema for {}", getKeyspaceTable(), e);
            throw new RuntimeException("Failed to discover table schema", e);
        }
    }

    public List<String> getPrimaryKeyNames() {
        return new ArrayList<>(primaryKeyNames);
    }

    public List<String> getAllColumnNames() {
        return new ArrayList<>(allColumnNames);
    }

    public String getPostgresType(String columnName) {
        return columnNameToPostgresTypeMap.get(columnName);
    }

    public Class<?> getBindClass(int index) {
        if (index < 0 || index >= bindClasses.size()) {
            return null;
        }
        return bindClasses.get(index);
    }

    public Class<?> getBindClass(String columnName) {
        int index = allColumnNames.indexOf(columnName);
        return getBindClass(index);
    }

    public List<Class<?>> getBindClasses() {
        return new ArrayList<>(bindClasses);
    }

    public DataTypeMapper getDataTypeMapper() {
        return dataTypeMapper;
    }

    @Override
    public List<String> getColumnNames(boolean format) {
        return new ArrayList<>(allColumnNames);
    }

    @Override
    public List<DataType> getColumnCqlTypes() {
        // For YugabyteDB, we don't use CQL types, but we need to implement this for compatibility
        // Return empty list since we're using PostgreSQL types instead
        return new ArrayList<>();
    }
}
