# Quick Fix for Connection Error

## Error: "Lost connection to remote peer" / "Could not reach any contact point"

### Immediate Actions

#### 1. Verify Basic Connectivity
```bash
# Test if the host is reachable
ping vcause27kuat02c.azr.bank-dns.com

# Test if port 9042 is open
nc -zv vcause27kuat02c.azr.bank-dns.com 9042
# OR
telnet vcause27kuat02c.azr.bank-dns.com 9042
```

#### 2. Test with cqlsh
```bash
# Try connecting with cqlsh first
cqlsh vcause27kuat02c.azr.bank-dns.com 9042 -u your_username -p your_password

# If that fails, try with SSL
cqlsh vcause27kuat02c.azr.bank-dns.com 9042 -u your_username -p your_password --ssl
```

#### 3. Most Common Fix: Enable SSL (if required)

If your cluster requires SSL, update your properties file:

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

#### 4. Use IP Address Instead of Hostname

If hostname resolution is the issue, try using the IP address from the error:
```properties
# From error: 100.75.64.54
spark.cdm.connect.origin.host=100.75.64.54
spark.cdm.connect.target.host=100.75.64.54
```

#### 5. Check Your Properties File Hostname

The error shows `vcause27kuat02c.azr.bank-dns.com` but your properties file might have a different hostname. Make sure they match:

```properties
# Verify this matches the hostname in the error
spark.cdm.connect.origin.host=vcause27kuat02c.azr.bank-dns.com
```

#### 6. Increase Connection Timeouts (Already Added)

The properties file has been updated with connection timeout settings. If you still get timeouts, increase these values:

```properties
spark.cassandra.connection.timeout.ms=60000  # Increase from 30000
spark.network.timeout=900s  # Increase from 600s
```

### Diagnostic Checklist

- [ ] Can you ping the host? (`ping vcause27kuat02c.azr.bank-dns.com`)
- [ ] Is port 9042 accessible? (`nc -zv vcause27kuat02c.azr.bank-dns.com 9042`)
- [ ] Does cqlsh connect? (`cqlsh vcause27kuat02c.azr.bank-dns.com 9042`)
- [ ] Are credentials correct? (username/password)
- [ ] Does the cluster require SSL? (check with your DBA)
- [ ] Are firewall rules configured? (check with network team)
- [ ] Does hostname match in properties file?

### Next Steps

1. If cqlsh works but CDM doesn't → Check SSL configuration
2. If cqlsh doesn't work → Check network/firewall/credentials
3. If ping fails → Check network connectivity/DNS
4. If port test fails → Check firewall rules

For detailed troubleshooting, see `CONNECTION_TROUBLESHOOTING.md`

