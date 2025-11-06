# YugabyteDB Smart Driver Setup Guide

This guide explains the changes made to use YugabyteDB's JDBC Smart Driver instead of the standard PostgreSQL JDBC driver.

## What is the YugabyteDB Smart Driver?

The **YugabyteDB JDBC Smart Driver** is an enhanced JDBC driver that provides:

- ✅ **Load Balancing**: Automatically distributes connections across cluster nodes
- ✅ **Topology Awareness**: Routes queries to the nearest node (datacenter/region/zone)
- ✅ **Better Performance**: Optimized for YugabyteDB's distributed architecture
- ✅ **SSL Support**: Configurable SSL/TLS encryption
- ✅ **Connection Pooling**: Better connection management

## Changes Made

### 1. Updated Maven Dependency (`pom.xml`)

**Before:**
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
```

**After:**
```xml
<dependency>
    <groupId>com.yugabyte</groupId>
    <artifactId>jdbc-yugabytedb</artifactId>
    <version>42.7.3-yb-4</version>
</dependency>
```

### 2. Updated JDBC URL (`YugabyteSession.java`)

**Before:**
```java
String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
```

**After:**
```java
String url = String.format("jdbc:yugabytedb://%s:%s/%s", host, port, database);
```

### 3. Added Smart Driver Features

- **Load Balancing**: Enabled by default
- **Topology Awareness**: Auto-detects cluster topology
- **Configurable SSL**: Can be enabled via properties file

### 4. Made SSL Configurable

**Before:** SSL was hardcoded to disabled

**After:** SSL can be configured via properties:

```properties
# Enable SSL for YugabyteDB YSQL
spark.cdm.connect.target.yugabyte.ssl.enabled=true
spark.cdm.connect.target.yugabyte.sslmode=require
spark.cdm.connect.target.yugabyte.sslrootcert=/path/to/ca.crt
```

## Configuration Options

### Basic Configuration (No SSL)

```properties
# YugabyteDB YSQL Connection
spark.cdm.connect.target.yugabyte.host=your-host
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=yugabyte
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=password
```

### With SSL Enabled

```properties
# YugabyteDB YSQL Connection
spark.cdm.connect.target.yugabyte.host=your-host
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=yugabyte
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=password

# SSL Configuration
spark.cdm.connect.target.yugabyte.ssl.enabled=true
spark.cdm.connect.target.yugabyte.sslmode=require
spark.cdm.connect.target.yugabyte.sslrootcert=/path/to/ca.crt
```

### SSL Mode Options

- `disable`: SSL disabled (default)
- `allow`: Try SSL, fallback to non-SSL
- `prefer`: Prefer SSL, fallback to non-SSL
- `require`: Require SSL, but don't verify certificate
- `verify-ca`: Require SSL and verify CA certificate
- `verify-full`: Require SSL and verify full certificate chain (most secure)

## Smart Driver Features

### Load Balancing

The Smart Driver automatically balances connections across all nodes in your YugabyteDB cluster. This is enabled by default with:

```java
props.setProperty("load-balance", "true");
```

### Topology Awareness

You can specify topology keys for better routing:

```properties
# Optional: Specify topology for better routing
# Format: datacenter:region:zone
spark.cdm.connect.target.yugabyte.topology-keys=us-east-1:us-east-1a:zone1
```

Or leave empty to auto-detect:
```java
props.setProperty("topology-keys", ""); // Auto-detect
```

### Connection Pooling

The Smart Driver uses connection pooling automatically. You can configure pool size:

```properties
# Connection pool settings (if needed)
spark.cdm.connect.target.yugabyte.maxConnections=10
```

## Benefits Over Standard PostgreSQL Driver

| Feature | PostgreSQL Driver | YugabyteDB Smart Driver |
|---------|------------------|------------------------|
| Load Balancing | ❌ No | ✅ Yes |
| Topology Awareness | ❌ No | ✅ Yes |
| SSL Configurable | ✅ Yes | ✅ Yes (better) |
| Performance | Standard | Optimized for YugabyteDB |
| Connection Management | Basic | Advanced |

## Migration Steps

1. **Update Dependencies**:
   ```bash
   mvn clean install
   ```

2. **Update Properties File** (if using SSL):
   ```properties
   spark.cdm.connect.target.yugabyte.ssl.enabled=true
   spark.cdm.connect.target.yugabyte.sslmode=require
   spark.cdm.connect.target.yugabyte.sslrootcert=/path/to/ca.crt
   ```

3. **Rebuild JAR**:
   ```bash
   mvn clean package
   ```

4. **Test Connection**:
   ```bash
   # Run your migration command
   spark-submit --properties-file your-properties.properties ...
   ```

## Troubleshooting

### Error: "Driver not found"

**Solution:** Ensure the Smart Driver dependency is in your classpath:
```bash
mvn dependency:tree | grep yugabytedb
```

### Error: "Connection refused"

**Solution:** Verify the JDBC URL format:
- ✅ Correct: `jdbc:yugabytedb://host:port/database`
- ❌ Wrong: `jdbc:postgresql://host:port/database`

### SSL Connection Issues

**Solution:** Check SSL configuration:
```properties
# If SSL is required by YugabyteDB
spark.cdm.connect.target.yugabyte.ssl.enabled=true
spark.cdm.connect.target.yugabyte.sslmode=require
spark.cdm.connect.target.yugabyte.sslrootcert=/path/to/ca.crt
```

### Load Balancing Not Working

**Solution:** Ensure you're using the Smart Driver URL:
- ✅ `jdbc:yugabytedb://...`
- ❌ `jdbc:postgresql://...`

## Additional Resources

- [YugabyteDB Smart Driver Documentation](https://docs.yugabyte.com/preview/drivers-orms/java/yugabyte-jdbc-reference/)
- [Smart Driver Connection Properties](https://docs.yugabyte.com/preview/drivers-orms/java/yugabyte-jdbc-reference/#connection-properties)
- [SSL Configuration Guide](https://docs.yugabyte.com/preview/secure/tls-encryption/)

## Summary

✅ **Switched from PostgreSQL JDBC to YugabyteDB Smart Driver**  
✅ **Added load balancing and topology awareness**  
✅ **Made SSL configurable (no longer hardcoded)**  
✅ **Better performance for YugabyteDB clusters**  

The Smart Driver is backward compatible with PostgreSQL JDBC, so existing code continues to work while gaining additional benefits!

