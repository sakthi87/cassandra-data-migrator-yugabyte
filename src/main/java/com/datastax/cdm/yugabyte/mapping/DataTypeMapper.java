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
package com.datastax.cdm.yugabyte.mapping;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps PostgreSQL data types to Java classes for YugabyteDB YSQL
 */
public class DataTypeMapper {
    private static final Logger logger = LoggerFactory.getLogger(DataTypeMapper.class);

    private static final Map<Integer, Class<?>> SQL_TYPE_TO_JAVA_CLASS = new HashMap<>();
    private static final Map<String, Class<?>> POSTGRES_TYPE_TO_JAVA_CLASS = new HashMap<>();

    static {
        // SQL Type mappings
        SQL_TYPE_TO_JAVA_CLASS.put(Types.BOOLEAN, Boolean.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.TINYINT, Byte.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.SMALLINT, Short.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.INTEGER, Integer.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.BIGINT, Long.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.REAL, Float.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.FLOAT, Double.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.DOUBLE, Double.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.NUMERIC, BigDecimal.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.DECIMAL, BigDecimal.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.CHAR, String.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.VARCHAR, String.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.LONGVARCHAR, String.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.DATE, LocalDate.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.TIME, LocalTime.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.TIMESTAMP, LocalDateTime.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.BINARY, byte[].class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.VARBINARY, byte[].class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.LONGVARBINARY, byte[].class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.OTHER, Object.class);

        // PostgreSQL specific type mappings
        POSTGRES_TYPE_TO_JAVA_CLASS.put("uuid", UUID.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("text", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("json", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("jsonb", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("inet", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("cidr", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("macaddr", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("point", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("line", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("lseg", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("box", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("path", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("polygon", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("circle", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("interval", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("bytea", byte[].class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("bit", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("varbit", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("tsvector", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("tsquery", String.class);
        POSTGRES_TYPE_TO_JAVA_CLASS.put("array", String.class);
    }

    /**
     * Maps Cassandra CQL types to PostgreSQL types
     */
    public static String mapCassandraToPostgres(String cassandraType) {
        if (cassandraType == null) {
            return "text";
        }

        String lowerType = cassandraType.toLowerCase();

        switch (lowerType) {
        case "text":
        case "varchar":
            return "text";
        case "int":
            return "integer";
        case "bigint":
            return "bigint";
        case "smallint":
            return "smallint";
        case "tinyint":
            return "smallint";
        case "float":
            return "real";
        case "double":
            return "double precision";
        case "decimal":
            return "numeric";
        case "boolean":
            return "boolean";
        case "uuid":
            return "uuid";
        case "timeuuid":
            return "uuid";
        case "timestamp":
            return "timestamp";
        case "date":
            return "date";
        case "time":
            return "time";
        case "blob":
            return "bytea";
        case "inet":
            return "inet";
        case "counter":
            return "bigint";
        case "ascii":
            return "text";
        case "varint":
            return "numeric";
        case "duration":
            return "interval";
        default:
            if (lowerType.startsWith("list<") || lowerType.startsWith("set<") || lowerType.startsWith("map<")) {
                return "text"; // Store collections as JSON text
            }
            if (lowerType.startsWith("frozen<")) {
                return "text"; // Store UDTs as JSON text
            }
            logger.warn("Unknown Cassandra type: {}, mapping to text", cassandraType);
            return "text";
        }
    }

    /**
     * Gets Java class for PostgreSQL data type
     */
    public Class<?> getJavaClass(int sqlType, String postgresType) {
        // First try PostgreSQL specific types
        if (postgresType != null) {
            Class<?> postgresClass = POSTGRES_TYPE_TO_JAVA_CLASS.get(postgresType.toLowerCase());
            if (postgresClass != null) {
                return postgresClass;
            }
        }

        // Fall back to SQL type mapping
        Class<?> sqlClass = SQL_TYPE_TO_JAVA_CLASS.get(sqlType);
        if (sqlClass != null) {
            return sqlClass;
        }

        // Default to String for unknown types
        logger.warn("Unknown data type: SQL_TYPE={}, POSTGRES_TYPE={}, defaulting to String", sqlType, postgresType);
        return String.class;
    }

    /**
     * Converts Cassandra value to PostgreSQL compatible value
     */
    public Object convertValue(Object cassandraValue, String cassandraType, Class<?> targetJavaClass) {
        if (cassandraValue == null) {
            return null;
        }

        try {
            // Handle collections and UDTs
            if (cassandraType != null && (cassandraType.toLowerCase().startsWith("list<")
                    || cassandraType.toLowerCase().startsWith("set<") || cassandraType.toLowerCase().startsWith("map<")
                    || cassandraType.toLowerCase().startsWith("frozen<"))) {
                // Convert collections to JSON string
                return cassandraValue.toString();
            }

            // Handle UUID types
            if (cassandraType != null
                    && (cassandraType.toLowerCase().equals("uuid") || cassandraType.toLowerCase().equals("timeuuid"))) {
                if (cassandraValue instanceof UUID) {
                    return cassandraValue;
                } else if (cassandraValue instanceof String) {
                    return UUID.fromString((String) cassandraValue);
                }
            }

            // Handle timestamp conversion
            if (cassandraType != null && cassandraType.toLowerCase().equals("timestamp")) {
                if (cassandraValue instanceof java.time.Instant) {
                    return ((java.time.Instant) cassandraValue).atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                } else if (cassandraValue instanceof java.util.Date) {
                    return ((java.util.Date) cassandraValue).toInstant().atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                } else if (cassandraValue instanceof Long) {
                    return java.time.Instant.ofEpochMilli((Long) cassandraValue)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                }
            }

            // Handle counter type
            if (cassandraType != null && cassandraType.toLowerCase().equals("counter")) {
                if (cassandraValue instanceof BigInteger) {
                    return ((BigInteger) cassandraValue).longValue();
                }
            }

            // Handle Instant to LocalDateTime conversion (common case)
            if (cassandraValue instanceof java.time.Instant && targetJavaClass == LocalDateTime.class) {
                return ((java.time.Instant) cassandraValue).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            }

            // Direct conversion if types match
            if (targetJavaClass.isAssignableFrom(cassandraValue.getClass())) {
                return cassandraValue;
            }

            // String conversion for most types
            if (targetJavaClass == String.class) {
                return cassandraValue.toString();
            }

            // Numeric conversions
            if (targetJavaClass == Integer.class && cassandraValue instanceof Number) {
                return ((Number) cassandraValue).intValue();
            }
            if (targetJavaClass == Long.class && cassandraValue instanceof Number) {
                return ((Number) cassandraValue).longValue();
            }
            if (targetJavaClass == Double.class && cassandraValue instanceof Number) {
                return ((Number) cassandraValue).doubleValue();
            }
            if (targetJavaClass == Float.class && cassandraValue instanceof Number) {
                return ((Number) cassandraValue).floatValue();
            }
            if (targetJavaClass == BigDecimal.class && cassandraValue instanceof Number) {
                return BigDecimal.valueOf(((Number) cassandraValue).doubleValue());
            }

            // Boolean conversion
            if (targetJavaClass == Boolean.class) {
                if (cassandraValue instanceof Boolean) {
                    return cassandraValue;
                } else if (cassandraValue instanceof String) {
                    return Boolean.parseBoolean((String) cassandraValue);
                }
            }

            logger.warn("Could not convert value {} of type {} to target class {}", cassandraValue,
                    cassandraValue.getClass().getSimpleName(), targetJavaClass.getSimpleName());
            return cassandraValue.toString();

        } catch (Exception e) {
            logger.error("Error converting value {} of type {} to target class {}", cassandraValue,
                    cassandraValue.getClass().getSimpleName(), targetJavaClass.getSimpleName(), e);
            return cassandraValue.toString();
        }
    }
}
