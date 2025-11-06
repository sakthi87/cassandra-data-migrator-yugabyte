# How to Check YugabyteDB SSL Configuration

This guide explains how to verify SSL settings for YSQL and YCQL in your YugabyteDB universe.

## Quick Test Method (Recommended)

### Test 1: YSQL Connection Test

```bash
# Test YSQL WITHOUT SSL (should work if SSL is optional)
psql -h your-yugabyte-host -p 5433 -U yugabyte -d yugabyte

# If above works, try WITH SSL required
psql "host=your-yugabyte-host port=5433 user=yugabyte dbname=yugabyte sslmode=require"

# Test YSQL WITH SSL but optional
psql "host=your-yugabyte-host port=5433 user=yugabyte dbname=yugabyte sslmode=prefer"
```

**Interpretation:**
- ✅ `psql` works without SSL → SSL is **optional** (not enforced)
- ❌ `psql` fails without SSL but works with `sslmode=require` → SSL is **required** (enforced)
- ✅ Both work → SSL is **optional**

### Test 2: YCQL Connection Test

```bash
# Test YCQL WITHOUT SSL
cqlsh your-yugabyte-host 9042 -u yugabyte -p password

# Test YCQL WITH SSL
cqlsh your-yugabyte-host 9042 -u yugabyte -p password --ssl
```

**Interpretation:**
- ✅ Works without `--ssl` → SSL is **optional** (not enforced)
- ❌ Fails without `--ssl` but works with `--ssl` → SSL is **required** (enforced)
- ✅ Both work → SSL is **optional**

## Method 1: YugabyteDB Admin UI

### Access YugabyteDB Admin UI

1. **Open YugabyteDB Admin UI**:
   ```
   http://your-yugabyte-host:7000
   ```

2. **Navigate to Configuration**:
   - Go to **Configuration** or **Settings**
   - Look for **Security** or **Encryption** settings

3. **Check SSL Settings**:
   - Look for **"Encryption in Transit"** or **"TLS/SSL"** settings
   - Check settings for:
     - **YSQL (PostgreSQL)**: Port 5433 SSL configuration
     - **YCQL (Cassandra)**: Port 9042 SSL configuration

### In YugabyteDB Anywhere (YBA)

1. **Access YBA UI**:
   ```
   http://your-yba-host
   ```

2. **Navigate to Universe**:
   - Select your universe
   - Go to **Configuration** → **Security** or **Encryption**

3. **Check SSL Configuration**:
   - Look for **"Encryption in Transit"**
   - Check **"Client to Server"** encryption settings
   - Verify **"Require SSL"** or **"SSL Required"** flags

## Method 2: Command Line (YugabyteDB Nodes)

### Check Configuration Files

#### On YugabyteDB Node:

```bash
# SSH into YugabyteDB node
ssh user@yugabyte-node

# Check YSQL (PostgreSQL) SSL configuration
grep -i ssl /opt/yugabyte/tserver/conf/server.conf
grep -i ssl /opt/yugabyte/tserver/conf/postgresql.conf

# Check YCQL SSL configuration
grep -i ssl /opt/yugabyte/tserver/conf/cassandra.yaml
grep -i "use_client_to_server_encryption" /opt/yugabyte/tserver/conf/cassandra.yaml
grep -i "require_client_auth" /opt/yugabyte/tserver/conf/cassandra.yaml
```

#### Key Configuration Files:

**YSQL Configuration:**
```bash
# PostgreSQL configuration
cat /opt/yugabyte/tserver/conf/postgresql.conf | grep -i ssl

# Look for:
# ssl = on/off
# ssl_cert_file = ...
# require_ssl = on/off  ← This determines if SSL is required
```

**YCQL Configuration:**
```bash
# Cassandra configuration
cat /opt/yugabyte/tserver/conf/cassandra.yaml | grep -i -A 5 "client_encryption_options"

# Look for:
# client_encryption_options:
#   enabled: true/false
#   require_client_auth: true/false  ← This determines if SSL is required
```

### Check via yb-admin

```bash
# Connect to YugabyteDB cluster
yb-admin --master_addresses your-master-address:7100 list_all_masters

# Check cluster configuration
yb-admin --master_addresses your-master-address:7100 get_universe_config
```

## Method 3: Check via Database Queries

### YSQL: Check SSL Settings

```bash
# Connect to YSQL
psql -h your-host -p 5433 -U yugabyte -d yugabyte

# Check SSL status
SHOW ssl;
SHOW ssl_cert_file;
SHOW require_ssl;

# Check current connection SSL status
SELECT * FROM pg_stat_ssl WHERE pid = pg_backend_pid();
```

**Expected Output:**
```
ssl | off/on
require_ssl | off/on  ← If 'on', SSL is required
```

### YCQL: Check SSL Settings

```bash
# Connect to YCQL
cqlsh your-host 9042 -u yugabyte -p password

# Check cluster configuration
DESCRIBE CLUSTER;

# Check SSL encryption status (requires system access)
SELECT * FROM system_schema.keyspaces;
```

## Method 4: Check via Logs

### Check YugabyteDB TServer Logs

```bash
# SSH into YugabyteDB node
ssh user@yugabyte-node

# Check TServer logs for SSL configuration
grep -i ssl /opt/yugabyte/tserver/logs/yb-tserver.*.log | tail -20

# Check for SSL initialization messages
grep -i "ssl\|tls\|encryption" /opt/yugabyte/tserver/logs/yb-tserver.*.log | grep -i "start\|init\|config"
```

### Look for These Messages:

**YSQL SSL:**
```
SSL enabled for PostgreSQL connections
SSL required for client connections
```

**YCQL SSL:**
```
Client-to-server encryption enabled
Client authentication required
```

## Method 5: Network-Level Testing

### Test SSL Handshake

```bash
# Test YSQL SSL handshake
openssl s_client -connect your-host:5433 -starttls postgres

# Test YCQL SSL handshake
openssl s_client -connect your-host:9042

# If SSL is enabled, you'll see certificate details
# If SSL is not enabled, connection will fail or show plain text
```

### Check with nmap

```bash
# Check if SSL ports are open
nmap -p 5433,9042 your-yugabyte-host

# Check SSL/TLS versions supported
nmap --script ssl-enum-ciphers -p 5433,9042 your-yugabyte-host
```

## Method 6: Check YugabyteDB Process Arguments

### Check Running Processes

```bash
# SSH into YugabyteDB node
ssh user@yugabyte-node

# Check TServer process arguments
ps aux | grep yb-tserver | grep -i ssl

# Check for SSL-related flags:
# --use_client_to_server_encryption=true/false
# --certs_dir_name=/path/to/certs
# --ssl_protocols=...
```

## Practical Script to Check All Settings

Create this script to check everything:

```bash
#!/bin/bash
# check_yugabyte_ssl.sh

HOST="${1:-your-yugabyte-host}"
YSQL_PORT=5433
YCQL_PORT=9042

echo "=========================================="
echo "YugabyteDB SSL Configuration Checker"
echo "=========================================="
echo "Host: $HOST"
echo ""

# Test YSQL
echo "Testing YSQL (PostgreSQL) on port $YSQL_PORT..."
if psql -h "$HOST" -p "$YSQL_PORT" -U yugabyte -d yugabyte -c "SELECT 1;" 2>/dev/null; then
    echo "✅ YSQL: Non-SSL connection works"
    YSQL_SSL_OPTIONAL=true
else
    echo "❌ YSQL: Non-SSL connection failed"
    if psql "host=$HOST port=$YSQL_PORT user=yugabyte dbname=yugabyte sslmode=require" -c "SELECT 1;" 2>/dev/null; then
        echo "✅ YSQL: SSL connection works"
        echo "⚠️  YSQL: SSL is REQUIRED"
        YSQL_SSL_REQUIRED=true
    else
        echo "❌ YSQL: Both SSL and non-SSL failed"
        YSQL_SSL_REQUIRED=false
    fi
fi

echo ""

# Test YCQL
echo "Testing YCQL (Cassandra) on port $YCQL_PORT..."
if cqlsh "$HOST" "$YCQL_PORT" -u yugabyte -p password -e "SELECT release_version FROM system.local;" 2>/dev/null; then
    echo "✅ YCQL: Non-SSL connection works"
    YCQL_SSL_OPTIONAL=true
else
    echo "❌ YCQL: Non-SSL connection failed"
    if cqlsh "$HOST" "$YCQL_PORT" -u yugabyte -p password --ssl -e "SELECT release_version FROM system.local;" 2>/dev/null; then
        echo "✅ YCQL: SSL connection works"
        echo "⚠️  YCQL: SSL is REQUIRED"
        YCQL_SSL_REQUIRED=true
    else
        echo "❌ YCQL: Both SSL and non-SSL failed"
        YCQL_SSL_REQUIRED=false
    fi
fi

echo ""
echo "=========================================="
echo "Summary"
echo "=========================================="
echo ""

if [ "$YSQL_SSL_OPTIONAL" = true ]; then
    echo "YSQL: SSL is OPTIONAL (accepts both SSL and non-SSL)"
elif [ "$YSQL_SSL_REQUIRED" = true ]; then
    echo "YSQL: SSL is REQUIRED (only accepts SSL)"
else
    echo "YSQL: Connection test failed - check network/credentials"
fi

if [ "$YCQL_SSL_OPTIONAL" = true ]; then
    echo "YCQL: SSL is OPTIONAL (accepts both SSL and non-SSL)"
elif [ "$YCQL_SSL_REQUIRED" = true ]; then
    echo "YCQL: SSL is REQUIRED (only accepts SSL)"
else
    echo "YCQL: Connection test failed - check network/credentials"
fi
```

## Common Configuration Patterns

### Pattern 1: SSL Optional (Both Protocols)

```
YSQL: SSL enabled, require_ssl = false
YCQL: SSL enabled, require_client_auth = false
Result: Both accept SSL and non-SSL connections
```

### Pattern 2: SSL Required for YCQL Only

```
YSQL: SSL enabled, require_ssl = false
YCQL: SSL enabled, require_client_auth = true
Result: YSQL accepts both, YCQL requires SSL
```

### Pattern 3: SSL Required for Both

```
YSQL: SSL enabled, require_ssl = true
YCQL: SSL enabled, require_client_auth = true
Result: Both require SSL
```

### Pattern 4: SSL Disabled

```
YSQL: SSL disabled
YCQL: SSL disabled
Result: Neither accepts SSL connections
```

## Quick Reference Table

| Test Result | Interpretation |
|-------------|----------------|
| Non-SSL works, SSL works | SSL is **optional** |
| Non-SSL fails, SSL works | SSL is **required** |
| Non-SSL works, SSL fails | SSL partially enabled (rare) |
| Both fail | Network/credentials issue |

## Next Steps Based on Results

### If YSQL SSL is Optional:
- ✅ Current code works (hardcoded `ssl=false`)
- No changes needed

### If YSQL SSL is Required:
- ❌ Current code will fail
- Need to modify `YugabyteSession.java` to enable SSL

### If YCQL SSL is Required:
- ❌ Current code will fail (SSL disabled by default)
- ✅ Enable SSL in properties file with truststore

### If YCQL SSL is Optional:
- ✅ Current code should work
- If it doesn't, check other connection issues

## Troubleshooting

### Can't Access Admin UI?
- Check firewall rules
- Verify YugabyteDB is running
- Check network connectivity

### Can't SSH to Nodes?
- Use `kubectl exec` if running on Kubernetes
- Use cloud provider console if on cloud
- Check with your DBA/admin

### Commands Not Found?
- Install PostgreSQL client for `psql`
- Install Cassandra tools for `cqlsh`
- Use Docker containers if tools unavailable

## Summary

The **easiest method** is the connection test:
1. Try connecting without SSL
2. Try connecting with SSL
3. Compare results to determine if SSL is required

This doesn't require admin access and gives you the answer immediately!

