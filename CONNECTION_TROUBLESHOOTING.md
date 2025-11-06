# Connection Troubleshooting Guide

## Error: "Lost connection to remote peer" / "Could not reach any contact point"

This error typically occurs when the DataStax driver cannot establish a connection to the Cassandra/Yugabyte YCQL cluster.

### Common Causes

1. **Network/Firewall Issues**
   - Firewall blocking port 9042
   - Network connectivity problems
   - DNS resolution issues

2. **SSL/TLS Configuration Mismatch**
   - Cluster requires SSL but client has it disabled
   - Cluster has SSL disabled but client has it enabled
   - Invalid SSL certificates

3. **Connection Timeout Too Short**
   - Default timeout may be too aggressive for slow networks
   - Need to increase connection timeout settings

4. **Hostname Resolution Issues**
   - Hostname not resolving correctly
   - IP address vs hostname mismatch

### Troubleshooting Steps

#### Step 1: Test Basic Connectivity

```bash
# Test if you can reach the Cassandra/YCQL node
ping vcause27kuat02c.azr.bank-dns.com

# Test if port 9042 is accessible
telnet vcause27kuat02c.azr.bank-dns.com 9042
# OR
nc -zv vcause27kuat02c.azr.bank-dns.com 9042
```

#### Step 2: Test with cqlsh

```bash
# Try connecting with cqlsh to verify credentials and connectivity
cqlsh vcause27kuat02c.azr.bank-dns.com 9042 -u your_username -p your_password

# If SSL is required:
cqlsh vcause27kuat02c.azr.bank-dns.com 9042 -u your_username -p your_password --ssl
```

#### Step 3: Check SSL Configuration

If your cluster requires SSL, you need to enable it in the properties file:

```properties
# Enable SSL for origin
spark.cdm.connect.origin.tls.enabled=true
spark.cdm.connect.origin.tls.trustStore.path=/path/to/truststore.jks
spark.cdm.connect.origin.tls.trustStore.password=your_password
spark.cdm.connect.origin.tls.trustStore.type=JKS

# Enable SSL for target (if needed)
spark.cdm.connect.target.tls.enabled=true
spark.cdm.connect.target.tls.trustStore.path=/path/to/truststore.jks
spark.cdm.connect.target.tls.trustStore.password=your_password
spark.cdm.connect.target.tls.trustStore.type=JKS
```

#### Step 4: Increase Connection Timeouts

Add these settings to your properties file:

```properties
# Increase connection timeouts
spark.cassandra.connection.timeout.ms=60000
spark.cassandra.connection.keep_alive_ms=30000
spark.cassandra.connection.reconnection_delay_ms.min=1000
spark.cassandra.connection.reconnection_delay_ms.max=60000

# Network timeouts
spark.network.timeout=600s
spark.sql.broadcastTimeout=600s
```

#### Step 5: Verify Hostname Resolution

If using hostnames, try using IP addresses instead:

```properties
# Instead of hostname, try IP address
spark.cdm.connect.origin.host=100.75.64.54
spark.cdm.connect.target.host=100.75.64.54
```

#### Step 6: Check Multiple Contact Points

If your cluster has multiple nodes, specify all of them:

```properties
# Specify multiple contact points (comma-separated)
spark.cdm.connect.origin.host=node1.example.com,node2.example.com,node3.example.com
spark.cdm.connect.target.host=node1.example.com,node2.example.com,node3.example.com
```

### Updated Properties File Template

Here's a properties file with enhanced connection settings:

```properties
# =============================================================================
# CASSANDRA SOURCE CONFIGURATION
# =============================================================================
spark.cdm.connect.origin.host=vcause27kuat02c.azr.bank-dns.com
spark.cdm.connect.origin.port=9042
spark.cdm.connect.origin.username=your_username
spark.cdm.connect.origin.password=your_password
spark.cdm.connect.origin.localDC=datacenter1

# =============================================================================
# YUGABYTE YCQL TARGET CONFIGURATION
# =============================================================================
spark.cdm.connect.target.host=your-yugabyte-host
spark.cdm.connect.target.port=9042
spark.cdm.connect.target.username=yugabyte
spark.cdm.connect.target.password=your_password

# =============================================================================
# CONNECTION TIMEOUT SETTINGS (for troubleshooting)
# =============================================================================
# Increase these if you're getting connection timeouts
spark.cassandra.connection.timeout.ms=60000
spark.cassandra.connection.keep_alive_ms=30000
spark.cassandra.connection.reconnection_delay_ms.min=1000
spark.cassandra.connection.reconnection_delay_ms.max=60000
spark.cassandra.connection.local_dc=datacenter1

# Network timeouts
spark.network.timeout=600s
spark.sql.broadcastTimeout=600s

# =============================================================================
# SSL CONFIGURATION (if required)
# =============================================================================
# Uncomment and configure if your cluster requires SSL
# spark.cdm.connect.origin.tls.enabled=true
# spark.cdm.connect.origin.tls.trustStore.path=/path/to/truststore.jks
# spark.cdm.connect.origin.tls.trustStore.password=your_password

# =============================================================================
# SCHEMA CONFIGURATION
# =============================================================================
spark.cdm.schema.origin.keyspaceTable=your_keyspace.your_table
spark.cdm.schema.target.keyspaceTable=your_keyspace.your_table
```

### Additional Debugging

Enable verbose logging to see more connection details:

```properties
# Enable debug logging
spark.cdm.log.level=DEBUG
spark.cassandra.log.level=DEBUG
```

### Common Solutions

| Error | Solution |
|-------|----------|
| "Lost connection to remote peer" | Increase connection timeout, check network, verify SSL settings |
| "Could not reach any contact point" | Verify hostname/IP, check firewall, test with cqlsh |
| "Connection timeout" | Increase `spark.cassandra.connection.timeout.ms` |
| "SSL handshake failed" | Check SSL configuration, verify truststore path and password |
| "Authentication failed" | Verify username and password |

### Still Having Issues?

1. **Check cluster logs** on the Cassandra/Yugabyte side to see if connection attempts are being received
2. **Verify network connectivity** between your Spark client and the database cluster
3. **Check if the cluster is accepting connections** on port 9042
4. **Verify credentials** are correct
5. **Check if SSL is required** by the cluster and configure accordingly

