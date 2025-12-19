# LDAP Authentication Error Troubleshooting

## Error Analysis

**Error Message:**
```
FATAL: LDAP authentication failed for user "YBSQLUSER_DEV_EMER2"
```

**What This Means:**
- YugabyteDB is configured to use **LDAP authentication** (not password authentication)
- The username `YBSQLUSER_DEV_EMER2` is being used
- LDAP server rejected the authentication attempt

---

## Root Causes

### 1. **Incorrect Username/Password** (Most Common)

**Issue:** The username or password in your properties file doesn't match LDAP credentials.

**Check:**
```properties
# In transaction-test-audit.properties
spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2
spark.cdm.connect.target.yugabyte.password=your_password_here
```

**Solution:**
- Verify username is correct (case-sensitive)
- Verify password is correct
- Check if password has special characters that need escaping
- Ensure no extra spaces in username/password

---

### 2. **LDAP Server Not Reachable**

**Issue:** Spark machine cannot reach the LDAP server.

**Check:**
```bash
# Test LDAP connectivity (if LDAP server details are known)
# Check network connectivity to LDAP server
ping ldap-server-hostname

# Check LDAP port (usually 389 or 636 for SSL)
telnet ldap-server-hostname 389
```

**Solution:**
- Ensure Spark machine can reach LDAP server
- Check firewall rules
- Verify LDAP server is running

---

### 3. **LDAP User Doesn't Exist or Disabled**

**Issue:** User `YBSQLUSER_DEV_EMER2` doesn't exist in LDAP or is disabled.

**Solution:**
- Contact LDAP administrator to verify user exists
- Check if user account is active/enabled
- Verify user has necessary permissions

---

### 4. **LDAP Bind DN Configuration Issue**

**Issue:** YugabyteDB LDAP configuration might require specific bind DN format.

**Check YugabyteDB LDAP Configuration:**
```sql
-- Connect as admin and check LDAP settings
SELECT * FROM pg_settings WHERE name LIKE '%ldap%';
```

**Common LDAP Bind DN Formats:**
- `uid=username,ou=users,dc=example,dc=com`
- `CN=username,OU=Users,DC=example,DC=com`
- `username@domain.com`

---

### 5. **Password Expired or Needs Reset**

**Issue:** LDAP password might be expired or require reset.

**Solution:**
- Reset password in LDAP
- Update properties file with new password

---

### 6. **SSL/TLS Configuration Missing**

**Issue:** LDAP might require SSL/TLS but not configured in connection.

**Check:**
- YugabyteDB might require SSL for LDAP connections
- Connection might need SSL parameters

---

## Troubleshooting Steps

### Step 1: Verify Properties File

```bash
# Check your properties file
cat transaction-test-audit.properties | grep -E "username|password"

# Ensure no extra spaces or special characters
# Example of correct format:
spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2
spark.cdm.connect.target.yugabyte.password=YourPassword123
```

**Common Mistakes:**
- ❌ `username= YBSQLUSER_DEV_EMER2` (extra space)
- ❌ `username="YBSQLUSER_DEV_EMER2"` (quotes not needed)
- ❌ `password=password with spaces` (use quotes if needed)
- ✅ `username=YBSQLUSER_DEV_EMER2` (correct)

---

### Step 2: Test Connection Manually

**Using ysqlsh:**
```bash
# Test connection with same credentials
ysqlsh -h your-yugabyte-host -p 5433 -U YBSQLUSER_DEV_EMER2 -d your_database

# Or using psql
psql -h your-yugabyte-host -p 5433 -U YBSQLUSER_DEV_EMER2 -d your_database
```

**If this fails:** The issue is with credentials, not CDM.

**If this succeeds:** The issue might be with how CDM is passing credentials.

---

### Step 3: Check YugabyteDB LDAP Configuration

**Connect as admin and check:**
```sql
-- Check LDAP settings
SHOW hba_file;
SHOW ldapserver;
SHOW ldapbinddn;
SHOW ldapbindpasswd;
SHOW ldapsearchattribute;
SHOW ldapbasedn;
```

**Or check pg_hba.conf:**
```bash
# Find pg_hba.conf location
ysqlsh -c "SHOW hba_file;"

# Check LDAP configuration in pg_hba.conf
# Should have line like:
# host all all 0.0.0.0/0 ldap ldapserver=ldap.example.com ldapbinddn="cn=admin,dc=example,dc=com" ldapbindpasswd=password ldapsearchattribute=uid ldapbasedn="ou=users,dc=example,dc=com"
```

---

### Step 4: Verify LDAP User Format

**LDAP might expect different username format:**

**Option A: Full DN**
```properties
spark.cdm.connect.target.yugabyte.username=uid=YBSQLUSER_DEV_EMER2,ou=users,dc=example,dc=com
```

**Option B: UPN Format**
```properties
spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2@domain.com
```

**Option C: Simple Username (Current)**
```properties
spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2
```

**Check with LDAP administrator which format is required.**

---

### Step 5: Check Network Connectivity

```bash
# Test if Spark machine can reach YugabyteDB
ping your-yugabyte-host

# Test if YugabyteDB can reach LDAP server
# (This might require checking YugabyteDB logs)
```

---

### Step 6: Check YugabyteDB Logs

**On YugabyteDB server, check logs:**
```bash
# Check YugabyteDB logs for LDAP errors
tail -f /var/log/yugabyte/tserver/yb-tserver.*.log | grep -i ldap

# Or check PostgreSQL logs
tail -f /var/log/yugabyte/tserver/postgresql-*.log | grep -i ldap
```

**Look for:**
- LDAP connection errors
- Authentication failures
- Configuration issues

---

## Solutions

### Solution 1: Use Correct Credentials

**Update properties file with correct credentials:**
```properties
# Verify these match your LDAP credentials exactly
spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2
spark.cdm.connect.target.yugabyte.password=CorrectPassword123
```

**Test manually first:**
```bash
ysqlsh -h host -p 5433 -U YBSQLUSER_DEV_EMER2 -d database
# Enter password when prompted
```

---

### Solution 2: Use Different Authentication Method

**If LDAP is optional, switch to password authentication:**

**Check if YugabyteDB supports password auth:**
```sql
-- Check pg_hba.conf for password authentication option
-- Look for lines like:
-- host all all 0.0.0.0/0 md5
-- host all all 0.0.0.0/0 password
```

**If available, use a user with password authentication instead of LDAP.**

---

### Solution 3: Configure LDAP-Specific Connection Parameters

**If LDAP requires additional parameters, you might need to:**

1. **Check if connection string needs LDAP parameters:**
   ```properties
   # These might be needed (check YugabyteDB documentation)
   spark.cdm.connect.target.yugabyte.ldapserver=ldap.example.com
   spark.cdm.connect.target.yugabyte.ldapbinddn=cn=admin,dc=example,dc=com
   ```

2. **Note:** CDM might not support all LDAP parameters directly. You may need to:
   - Use a connection string with all parameters
   - Or modify YugabyteSession.java to add LDAP support

---

### Solution 4: Use Connection String with LDAP Parameters

**If standard username/password doesn't work, try connection string format:**

**Check YugabyteDB documentation for LDAP connection string format:**
```
jdbc:yugabytedb://host:port/database?user=YBSQLUSER_DEV_EMER2&password=pass&ldapserver=...
```

**Note:** This might require code changes to support LDAP-specific parameters.

---

## Quick Checklist

- [ ] **Verify username is correct** (case-sensitive, no spaces)
- [ ] **Verify password is correct** (no typos, special characters handled)
- [ ] **Test connection manually** with ysqlsh/psql
- [ ] **Check YugabyteDB LDAP configuration** (pg_hba.conf)
- [ ] **Verify LDAP server is reachable** from YugabyteDB
- [ ] **Check YugabyteDB logs** for detailed LDAP errors
- [ ] **Contact LDAP administrator** to verify user exists and is active
- [ ] **Check if password needs reset** in LDAP
- [ ] **Verify username format** (simple vs DN vs UPN)

---

## Common Fixes

### Fix 1: Correct Username/Password

```properties
# Remove any extra spaces
spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2
spark.cdm.connect.target.yugabyte.password=YourActualPassword
```

### Fix 2: Use Full LDAP DN (if required)

```properties
# If LDAP requires full DN
spark.cdm.connect.target.yugabyte.username=uid=YBSQLUSER_DEV_EMER2,ou=users,dc=example,dc=com
```

### Fix 3: Use UPN Format (if required)

```properties
# If LDAP requires UPN format
spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2@yourdomain.com
```

### Fix 4: Escape Special Characters in Password

```properties
# If password has special characters, ensure they're properly escaped
# Or use quotes if properties file supports it
spark.cdm.connect.target.yugabyte.password="Password@123#"
```

---

## Next Steps

1. **First:** Test connection manually with same credentials
2. **If manual test fails:** Fix credentials or contact LDAP admin
3. **If manual test succeeds:** Check CDM code for credential handling
4. **Check YugabyteDB logs** for more detailed error messages
5. **Contact YugabyteDB/LDAP administrator** if issue persists

---

## Additional Resources

- YugabyteDB LDAP Configuration: https://docs.yugabyte.com/preview/secure/authentication/ldap-authentication/
- PostgreSQL LDAP Authentication: https://www.postgresql.org/docs/current/auth-ldap.html

