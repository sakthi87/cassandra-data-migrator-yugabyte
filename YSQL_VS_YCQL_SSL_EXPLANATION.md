# YSQL vs YCQL SSL Configuration - Key Differences

## The Confusion Explained

You're experiencing different SSL behavior because **YSQL and YCQL use completely different protocols and SSL handling mechanisms** in YugabyteDB, even though they're on the same universe.

## Key Differences

| Aspect | YSQL (PostgreSQL) | YCQL (Cassandra) |
|--------|------------------|------------------|
| **Protocol** | PostgreSQL (JDBC) | Cassandra CQL (DataStax Driver) |
| **Port** | 5433 | 9042 |
| **SSL Handling** | **Hardcoded to DISABLED** in code | **Configurable** via properties file |
| **SSL Method** | PostgreSQL JDBC SSL settings | Java Truststore (JKS) |
| **Code Location** | `YugabyteSession.java` | `ConnectionFetcher.scala` |

## Why YSQL Works Without SSL

Looking at your code in `YugabyteSession.java` (lines 114-116):

```java
// SSL and connection settings to handle timeout issues
props.setProperty("ssl", "false"); // Disable SSL to avoid handshake timeouts
props.setProperty("sslmode", "disable"); // Explicitly disable SSL mode
props.setProperty("sslrootcert", ""); // No SSL certificate required
```

**YSQL SSL is hardcoded to DISABLED** in the code! This means:
- ⚠️ **Important**: This only works if YugabyteDB YSQL has SSL enabled but NOT enforced
- ✅ If YugabyteDB YSQL accepts non-SSL connections (SSL optional), this works
- ❌ If YugabyteDB YSQL REQUIRES SSL (SSL mandatory), this would FAIL
- ✅ No truststore needed for YSQL (assuming SSL is optional)
- ✅ The code explicitly disables SSL for YSQL connections

**Why it works for you:** Your YugabyteDB YSQL likely has SSL enabled but accepts non-SSL connections (SSL optional, not enforced).

## Why YCQL Fails With SSL

YCQL uses the **DataStax Java Driver** (same as Cassandra), which:
- Uses **Java Truststore (JKS)** for SSL certificates
- Has SSL **DISABLED by default** in CDM properties
- Requires explicit SSL configuration if YugabyteDB YCQL has SSL enabled

**The Problem:**
- Your YugabyteDB universe likely has **SSL enabled for YCQL** (port 9042)
- But CDM has **SSL disabled by default** for YCQL
- Result: Connection fails because of SSL mismatch

## Understanding YugabyteDB SSL Configuration

YugabyteDB can have **different SSL settings for YSQL and YCQL**:

```bash
# Check YugabyteDB SSL configuration
# YSQL (port 5433) - might have SSL disabled
# YCQL (port 9042) - might have SSL enabled
```

This is why:
- ✅ **YSQL works**: Code disables SSL, YugabyteDB YSQL might not require it
- ❌ **YCQL fails**: YugabyteDB YCQL requires SSL, but CDM has it disabled

## Solutions

### Solution 1: Enable SSL for YCQL (Recommended)

If your YugabyteDB YCQL has SSL enabled, you **must** enable SSL in CDM:

1. **Create truststore.jks** (see `YUGABYTE_YCQL_SSL_SETUP.md`):
   ```bash
   ./create_truststore.sh ca.crt YourPassword123!
   ```

2. **Update properties file**:
   ```properties
   # Enable SSL for YCQL target
   spark.cdm.connect.target.tls.enabled=true
   spark.cdm.connect.target.tls.trustStore.path=/path/to/truststore.jks
   spark.cdm.connect.target.tls.trustStore.password=YourPassword123!
   spark.cdm.connect.target.tls.trustStore.type=JKS
   ```

### Solution 2: Disable SSL on YugabyteDB YCQL (If Allowed)

If your security policy allows, you can disable SSL for YCQL on YugabyteDB:

```bash
# This requires YugabyteDB admin access
# Check YugabyteDB documentation for disabling SSL on YCQL port
```

**Note:** This may not be possible if your cluster has encryption-in-transit enabled.

### Solution 3: Check YugabyteDB SSL Configuration

First, verify what SSL settings your YugabyteDB has:

```bash
# Test YSQL connection (should work - SSL disabled in code)
psql -h your-yugabyte-host -p 5433 -U your_user -d your_db

# Test YCQL connection without SSL
cqlsh your-yugabyte-host 9042 -u your_user -p your_password

# Test YCQL connection with SSL
cqlsh your-yugabyte-host 9042 -u your_user -p your_password --ssl
```

**Interpretation:**
- If `cqlsh` works **WITHOUT** `--ssl` → YugabyteDB YCQL has SSL disabled
- If `cqlsh` works **ONLY WITH** `--ssl` → YugabyteDB YCQL requires SSL
- If `cqlsh` fails both ways → Check network/firewall/credentials

## Code Comparison

### YSQL SSL Handling (YugabyteSession.java)

```java
// Line 114-116: SSL is HARDCODED to disabled
props.setProperty("ssl", "false");
props.setProperty("sslmode", "disable");
props.setProperty("sslrootcert", "");
```

**No properties file configuration needed** - SSL is always off for YSQL.

### YCQL SSL Handling (ConnectionFetcher.scala)

```scala
// SSL is configurable via properties file
if (connectionDetails.trustStorePath.nonEmpty) {
  // SSL enabled with truststore
  .set("spark.cassandra.connection.ssl.enabled", "true")
  .set("spark.cassandra.connection.ssl.trustStore.path", ...)
} else {
  // SSL disabled by default
  .set("spark.cassandra.connection.ssl.enabled", connectionDetails.sslEnabled)
}
```

**Requires properties file configuration** to enable SSL for YCQL.

## Quick Diagnostic

Run these commands to understand your YugabyteDB SSL setup:

```bash
# 1. Test YSQL (should work - SSL disabled in code)
psql -h your-host -p 5433 -U yugabyte -d yugabyte

# 2. Test YCQL without SSL
cqlsh your-host 9042 -u yugabyte -p password

# 3. Test YCQL with SSL (if step 2 fails)
cqlsh your-host 9042 -u yugabyte -p password --ssl --certfile=/path/to/ca.crt

# 4. Check if SSL is required on YugabyteDB
# (If step 2 fails but step 3 works, SSL is required)
```

## Summary

| Scenario | YSQL | YCQL |
|----------|------|------|
| **SSL in code** | Hardcoded OFF | Configurable (default OFF) |
| **SSL on YugabyteDB** | May be OFF | Likely ON |
| **Your experience** | ✅ Works (SSL disabled) | ❌ Fails (SSL mismatch) |
| **Solution** | No action needed | Enable SSL in properties |

## Action Items

1. **Test YCQL connection**:
   ```bash
   cqlsh your-host 9042 -u user -p password --ssl
   ```

2. **If SSL is required**, enable it in properties:
   ```properties
   spark.cdm.connect.target.tls.enabled=true
   spark.cdm.connect.target.tls.trustStore.path=/path/to/truststore.jks
   spark.cdm.connect.target.tls.trustStore.password=YourPassword
   spark.cdm.connect.target.tls.trustStore.type=JKS
   ```

3. **If SSL is NOT required**, verify your YugabyteDB configuration allows non-SSL connections on port 9042.

## Why This Design?

- **YSQL**: Uses PostgreSQL JDBC, which has SSL built-in but can be complex. The code disables it to avoid timeout issues.
- **YCQL**: Uses DataStax driver (same as Cassandra), which requires Java truststore for SSL. This is standard Cassandra SSL configuration.

Both are correct approaches for their respective protocols, but they behave differently!

