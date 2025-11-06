# Yugabyte YCQL SSL/TLS Truststore Setup Guide

This guide explains how to set up a Java KeyStore (truststore.jks) for connecting to YugabyteDB YCQL with SSL/TLS encryption.

## Prerequisites

- Java `keytool` utility (comes with Java JDK)
- Root certificate (`ca.crt`) from your YugabyteDB cluster
- Access to your YugabyteDB cluster

## Step 1: Obtain the Root Certificate

### Option A: Download from YugabyteDB Cluster

If your YugabyteDB cluster has encryption in transit enabled, you need to get the root certificate:

```bash
# If you have access to the YugabyteDB nodes, the certificate is typically located at:
# /opt/yugabyte/tls/certs/ca.crt

# Copy it to your local machine
scp user@yugabyte-node:/opt/yugabyte/tls/certs/ca.crt ./ca.crt
```

### Option B: Get from YugabyteDB Admin UI

1. Log into YugabyteDB Admin UI
2. Navigate to Security/Encryption settings
3. Download the root CA certificate

### Option C: Extract from Existing Connection

If you're already connected to YugabyteDB, you can extract the certificate:

```bash
# Using openssl to get the certificate from a running connection
openssl s_client -showcerts -connect your-yugabyte-host:9042 </dev/null 2>/dev/null | openssl x509 -outform PEM > ca.crt
```

## Step 2: Create the Truststore JKS File

Use Java's `keytool` utility to import the certificate into a JKS truststore:

```bash
# Create truststore.jks and import the root certificate
keytool -importcert \
  -alias yugabyte-root-ca \
  -file ca.crt \
  -keystore truststore.jks \
  -storetype JKS \
  -storepass YourTruststorePassword \
  -noprompt
```

**Parameters explained:**
- `-alias yugabyte-root-ca`: Alias name for the certificate (you can use any name)
- `-file ca.crt`: Path to the root certificate file
- `-keystore truststore.jks`: Output path for the truststore file
- `-storetype JKS`: KeyStore type (JKS is the default)
- `-storepass YourTruststorePassword`: Password to protect the truststore (remember this!)
- `-noprompt`: Don't prompt for confirmation (useful for scripts)

### Example:

```bash
# Create a directory for SSL certificates
mkdir -p ~/yugabyte-ssl
cd ~/yugabyte-ssl

# Import the certificate
keytool -importcert \
  -alias yugabyte-root-ca \
  -file ca.crt \
  -keystore truststore.jks \
  -storetype JKS \
  -storepass MySecurePassword123! \
  -noprompt

# Verify the truststore was created
ls -lh truststore.jks
```

## Step 3: Verify the Truststore

Check that the certificate was imported correctly:

```bash
# List certificates in the truststore
keytool -list -v -keystore truststore.jks -storepass YourTruststorePassword

# You should see the yugabyte-root-ca certificate listed
```

## Step 4: Configure CDM Properties File

Update your `cassandra-to-ycql-migration.properties` file to use the truststore:

```properties
# =============================================================================
# SSL/TLS CONFIGURATION FOR YUGABYTE YCQL
# =============================================================================

# Target (Yugabyte YCQL) SSL Configuration
spark.cdm.connect.target.tls.enabled=true
spark.cdm.connect.target.tls.trustStore.path=/path/to/truststore.jks
spark.cdm.connect.target.tls.trustStore.password=YourTruststorePassword
spark.cdm.connect.target.tls.trustStore.type=JKS

# Optional: If client authentication is required (less common)
# spark.cdm.connect.target.tls.keyStore.path=/path/to/keystore.jks
# spark.cdm.connect.target.tls.keyStore.password=YourKeystorePassword

# Optional: Specify SSL algorithms (usually not needed)
# spark.cdm.connect.target.tls.enabledAlgorithms=TLS_RSA_WITH_AES_128_CBC_SHA,TLS_RSA_WITH_AES_256_CBC_SHA
```

### Example with Absolute Path:

```properties
# If truststore is in ~/yugabyte-ssl/truststore.jks
spark.cdm.connect.target.tls.enabled=true
spark.cdm.connect.target.tls.trustStore.path=/Users/yourusername/yugabyte-ssl/truststore.jks
spark.cdm.connect.target.tls.trustStore.password=MySecurePassword123!
spark.cdm.connect.target.tls.trustStore.type=JKS
```

### Example with Relative Path:

If you place the truststore in the same directory as your properties file:

```properties
spark.cdm.connect.target.tls.enabled=true
spark.cdm.connect.target.tls.trustStore.path=./truststore.jks
spark.cdm.connect.target.tls.trustStore.password=MySecurePassword123!
spark.cdm.connect.target.tls.trustStore.type=JKS
```

## Step 5: Test the Connection

### Test with cqlsh (if SSL is enabled on YugabyteDB):

```bash
# Test connection with SSL
cqlsh your-yugabyte-host 9042 \
  -u your_username \
  -p your_password \
  --ssl \
  --certfile=/path/to/ca.crt
```

### Test with CDM:

Run your migration command and check the logs for SSL connection messages:

```bash
spark-submit --properties-file cassandra-to-ycql-migration.properties \
--conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.your_table" \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.Migrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> ycql_migration_$(date +%Y%m%d_%H_%M).txt
```

Check the logs:
```bash
# Look for SSL connection messages
grep -i "ssl\|tls\|truststore" ycql_migration_*.txt
```

## Troubleshooting

### Error: "trustStore path does not exist"

**Solution:** Use absolute path or ensure the file is accessible:
```properties
# Use absolute path
spark.cdm.connect.target.tls.trustStore.path=/full/path/to/truststore.jks
```

### Error: "trustStore password is incorrect"

**Solution:** Verify the password matches what you used when creating the truststore:
```bash
# Test the password
keytool -list -keystore truststore.jks -storepass YourPassword
```

### Error: "Certificate chain validation failed"

**Solution:** Ensure you're using the correct root certificate:
```bash
# Verify certificate details
openssl x509 -in ca.crt -text -noout
```

### Error: "SSL handshake failed"

**Possible causes:**
1. **Wrong certificate**: Make sure you're using the root CA certificate from your YugabyteDB cluster
2. **Certificate expired**: Check certificate validity:
   ```bash
   openssl x509 -in ca.crt -noout -dates
   ```
3. **Hostname mismatch**: If using hostname, ensure the certificate matches

### Creating Truststore from Multiple Certificates

If you have intermediate certificates, you may need to import them:

```bash
# Import root certificate
keytool -importcert -alias root-ca -file root-ca.crt \
  -keystore truststore.jks -storepass YourPassword -noprompt

# Import intermediate certificate (if needed)
keytool -importcert -alias intermediate-ca -file intermediate-ca.crt \
  -keystore truststore.jks -storepass YourPassword -noprompt
```

## Security Best Practices

1. **Protect the truststore file:**
   ```bash
   # Set appropriate permissions
   chmod 600 truststore.jks
   chmod 600 truststore.properties  # If storing password separately
   ```

2. **Use strong password:**
   - Use a strong, unique password for the truststore
   - Don't commit passwords to version control

3. **Store password securely:**
   - Consider using environment variables for passwords
   - Or use a secrets management system

4. **Keep certificates updated:**
   - Renew certificates before they expire
   - Update truststore when certificates are renewed

## Complete Example

Here's a complete example workflow:

```bash
# 1. Get the certificate
scp user@yugabyte-node:/opt/yugabyte/tls/certs/ca.crt ./ca.crt

# 2. Create truststore
keytool -importcert \
  -alias yugabyte-root-ca \
  -file ca.crt \
  -keystore truststore.jks \
  -storetype JKS \
  -storepass MySecurePassword123! \
  -noprompt

# 3. Verify
keytool -list -v -keystore truststore.jks -storepass MySecurePassword123!

# 4. Update properties file
cat >> cassandra-to-ycql-migration.properties << EOF

# SSL Configuration
spark.cdm.connect.target.tls.enabled=true
spark.cdm.connect.target.tls.trustStore.path=/full/path/to/truststore.jks
spark.cdm.connect.target.tls.trustStore.password=MySecurePassword123!
spark.cdm.connect.target.tls.trustStore.type=JKS
EOF

# 5. Test connection
cqlsh your-yugabyte-host 9042 -u username -p password --ssl --certfile=ca.crt
```

## Additional Resources

- [YugabyteDB SSL/TLS Documentation](https://docs.yugabyte.com/preview/secure/tls-encryption/)
- [Java KeyTool Documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
- [DataStax Java Driver SSL Configuration](https://docs.datastax.com/en/developer/java-driver/4.19/manual/core/ssl/)

