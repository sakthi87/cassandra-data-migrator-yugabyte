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

import com.datastax.cdm.properties.IPropertyHelper;
import com.datastax.cdm.properties.KnownProperties;
import com.datastax.cdm.yugabyte.mapping.DataTypeMapper;
import com.datastax.oss.driver.api.core.type.DataType;

public class YugabyteTable extends BaseTable {
    private static final Logger logger = LoggerFactory.getLogger(YugabyteTable.class);

    private final Connection connection;
    private final IPropertyHelper propertyHelper;
    private final Map<String, String> columnNameToPostgresTypeMap = new HashMap<>();
    private final List<String> primaryKeyNames = new ArrayList<>();
    private final List<String> allColumnNames = new ArrayList<>();
    private final List<Class<?>> bindClasses = new ArrayList<>();
    private final DataTypeMapper dataTypeMapper;
    private String schemaName = "public"; // Default schema name

    public YugabyteTable(IPropertyHelper propertyHelper, boolean isOrigin, Connection connection) {
        super(propertyHelper, isOrigin);
        this.propertyHelper = propertyHelper;
        this.connection = connection;
        this.dataTypeMapper = new DataTypeMapper();

        // Discover table schema from YugabyteDB
        discoverTableSchema();
    }

    private void discoverTableSchema() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String[] tableParts = getKeyspaceTable().split("\\.");

            // Determine schema and table name
            // Format options:
            // 1. "table" -> use configured/default schema
            // 2. "schema.table" -> use specified schema
            // 3. "database.schema.table" -> use specified schema (database is in connection URL)
            String schema;
            String tableName;

            if (tableParts.length == 1) {
                // Just table name: "table" -> use configured schema or default to "public"
                schema = determineSchemaName();
                tableName = tableParts[0];
            } else if (tableParts.length == 2) {
                // Schema.table format: "schema.table"
                schema = tableParts[0];
                tableName = tableParts[1];
            } else if (tableParts.length == 3) {
                // Database.schema.table format: "database.schema.table"
                // Note: database is already in connection URL, so we use schema.table
                schema = tableParts[1];
                tableName = tableParts[2];
            } else {
                // Fallback: use configured schema and last part as table name
                schema = determineSchemaName();
                tableName = tableParts[tableParts.length - 1];
                logger.warn("Unexpected keyspaceTable format: {}. Using schema: {}, table: {}", getKeyspaceTable(),
                        schema, tableName);
            }

            this.schemaName = schema;

            logger.info("Discovering schema for table: {}.{} (database: {})", schema, tableName,
                    connection.getCatalog());

            // Try to get column information with the determined schema
            boolean tableFound = false;
            try (ResultSet columns = metaData.getColumns(null, schema, tableName, null)) {
                while (columns.next()) {
                    tableFound = true;
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

            // If table not found in specified schema, try to auto-detect schema
            if (!tableFound) {
                logger.warn("Table {} not found in schema {}. Attempting to auto-detect schema...", tableName, schema);
                schema = autoDetectSchema(metaData, tableName);
                if (schema != null) {
                    this.schemaName = schema;
                    logger.info("Auto-detected schema: {}. Retrying table discovery...", schema);

                    // Retry with detected schema
                    try (ResultSet columns = metaData.getColumns(null, schema, tableName, null)) {
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            String dataType = columns.getString("TYPE_NAME");
                            int dataTypeCode = columns.getInt("DATA_TYPE");

                            allColumnNames.add(columnName);
                            columnNameToPostgresTypeMap.put(columnName, dataType);

                            Class<?> bindClass = dataTypeMapper.getJavaClass(dataTypeCode, dataType);
                            bindClasses.add(bindClass);

                            logger.debug("Column: {} -> Type: {} -> Java Class: {}", columnName, dataType,
                                    bindClass.getSimpleName());
                        }
                    }
                } else {
                    throw new RuntimeException("Table " + tableName + " not found in schema " + schema
                            + " and could not auto-detect schema. Please specify the correct schema name.");
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

    /**
     * Get the schema name where the table is located.
     *
     * @return Schema name (e.g., "public", "my_schema")
     */
    public String getSchemaName() {
        return schemaName;
    }

    /**
     * Determine schema name from configuration or default to "public".
     */
    private String determineSchemaName() {
        String configuredSchema = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_SCHEMA);
        if (configuredSchema != null && !configuredSchema.trim().isEmpty()) {
            return configuredSchema.trim();
        }
        return "public"; // Default PostgreSQL schema
    }

    /**
     * Auto-detect schema by searching for the table across all schemas.
     *
     * @param metaData
     *            Database metadata
     * @param tableName
     *            Table name to search for
     *
     * @return Schema name if found, null otherwise
     */
    private String autoDetectSchema(DatabaseMetaData metaData, String tableName) {
        try {
            // Search in common schemas first
            String[] commonSchemas = { "public", "information_schema", "pg_catalog" };
            for (String schema : commonSchemas) {
                try (ResultSet columns = metaData.getColumns(null, schema, tableName, null)) {
                    if (columns.next()) {
                        logger.info("Found table {} in schema {}", tableName, schema);
                        return schema;
                    }
                }
            }

            // If not found in common schemas, search all schemas
            try (ResultSet schemas = metaData.getSchemas()) {
                while (schemas.next()) {
                    String schema = schemas.getString("TABLE_SCHEM");
                    // Skip system schemas
                    if (schema.equals("information_schema") || schema.equals("pg_catalog")
                            || schema.startsWith("pg_")) {
                        continue;
                    }

                    try (ResultSet columns = metaData.getColumns(null, schema, tableName, null)) {
                        if (columns.next()) {
                            logger.info("Found table {} in schema {}", tableName, schema);
                            return schema;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error during schema auto-detection", e);
        }
        return null;
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
