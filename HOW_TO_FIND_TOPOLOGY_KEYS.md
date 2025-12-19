# How to Find YugabyteDB Topology Keys

Topology keys are required to enable load balancing in YugabyteDB Smart Driver. They follow the format: `cloud.region.zone`

**Example:** `azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3`

---

## Method 1: YugabyteDB Admin UI (Easiest)

### Steps:

1. **Access YugabyteDB Admin UI:**
   - URL: `http://your-yugabyte-host:7000` (or your configured admin port)
   - Or: `https://your-yugabyte-host:7000` (if SSL enabled)

2. **Navigate to Nodes:**
   - Click on **"Nodes"** or **"Tablet Servers"** in the left menu
   - You'll see all nodes in your cluster

3. **Check Node Details:**
   - Click on each node to see details
   - Look for:
     - **Cloud:** `azure`, `aws`, `gcp`, `onprem`, etc.
     - **Region:** `centralus`, `eastus`, `westus`, etc.
     - **Zone:** `zone1`, `zone2`, `zone3`, `us-east-1a`, etc.

4. **Format Topology Keys:**
   - For each node, format as: `cloud.region.zone`
   - Combine all nodes with commas: `cloud.region.zone1,cloud.region.zone2,cloud.region.zone3`

**Example Output:**
```
Node 1: azure.centralus.zone1
Node 2: azure.centralus.zone2
Node 3: azure.centralus.zone3

Topology Keys: azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3
```

---

## Method 2: SQL Query (Recommended)

### Connect to YugabyteDB:

```bash
# Using ysqlsh (if available)
ysqlsh -h your-yugabyte-host -p 5433 -U yugabyte -d your_database

# Or using psql
psql -h your-yugabyte-host -p 5433 -U yugabyte -d your_database
```

### Query 1: Check Node Information

```sql
-- Get node information with cloud, region, zone
SELECT 
    host,
    port,
    cloud,
    region,
    zone,
    public_ip
FROM yb_servers();
```

**Expected Output:**
```
     host      | port | cloud |   region   |  zone  |  public_ip   
---------------+------+-------+-----------+--------+--------------
 10.0.1.1      | 5433 | azure | centralus | zone1  | 20.1.1.1
 10.0.1.2      | 5433 | azure | centralus | zone2  | 20.1.1.2
 10.0.1.3      | 5433 | azure | centralus | zone3  | 20.1.1.3
```

**Topology Keys:** `azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3`

### Query 2: Get Topology from pg_stat_activity

```sql
-- Check current connections and their topology
SELECT DISTINCT
    application_name,
    client_addr,
    cloud,
    region,
    zone
FROM pg_stat_activity
WHERE datname = current_database()
LIMIT 10;
```

### Query 3: Check System Tables

```sql
-- Query system catalog for node information
SELECT 
    node_id,
    host,
    cloud,
    region,
    zone
FROM pg_catalog.yb_servers();
```

---

## Method 3: YB Admin CLI

### If you have YB Admin CLI access:

```bash
# List all tablet servers
yb-admin --master_addresses your-master-address list_all_masters

# Get detailed node information
yb-admin --master_addresses your-master-address list_all_tablet_servers
```

**Output Example:**
```
Tablet Server UUID: abc123...
Host: 10.0.1.1
Port: 9100
Cloud: azure
Region: centralus
Zone: zone1
```

---

## Method 4: Kubernetes/Helm (If Applicable)

### If YugabyteDB is deployed via Kubernetes:

```bash
# Check node labels
kubectl get nodes --show-labels

# Check YugabyteDB pod labels
kubectl get pods -n yugabyte -l app=yb-tserver --show-labels

# Check Helm values
helm get values yugabyte -n yugabyte
```

**Look for labels:**
- `topology.kubernetes.io/zone`
- `failure-domain.beta.kubernetes.io/zone`
- `cloud.region.zone` (custom labels)

---

## Method 5: Connection String Inspection

### Check Existing Connection Strings:

If you have existing applications connecting to YugabyteDB, check their connection strings:

```bash
# Look for topology-keys in connection strings
grep -r "topology-keys\|topologyKeys" /path/to/config/
```

**Example Connection String:**
```
jdbc:yugabytedb://host:5433/database?topologyKeys=azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3
```

---

## Method 6: YugabyteDB Platform (If Using)

### If using YugabyteDB Platform/Cloud:

1. **Login to YugabyteDB Platform:**
   - Navigate to your cluster

2. **Check Cluster Details:**
   - Go to **"Clusters"** → Select your cluster
   - Click on **"Nodes"** tab
   - Each node shows: Cloud, Region, Zone

3. **Copy Topology Keys:**
   - Format: `cloud.region.zone1,cloud.region.zone2,cloud.region.zone3`

---

## Method 7: Azure-Specific (For Your Setup)

### Since you're using Azure Central:

```bash
# If you have Azure CLI access
az vm list --resource-group your-resource-group --show-details \
  --query "[].{Name:name, Location:location, Zone:zones}" \
  --output table
```

**Or check Azure Portal:**
1. Navigate to your Resource Group
2. Check Virtual Machines (YugabyteDB nodes)
3. Look at **Location** and **Availability Zone**
4. Format: `azure.{location}.zone{number}`

**Example:**
- Location: `Central US` → `centralus`
- Zone: `1`, `2`, `3`
- Topology Keys: `azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3`

---

## Quick Test Script

### Create a test script to find topology keys:

```bash
#!/bin/bash
# find_topology_keys.sh

HOST="your-yugabyte-host"
PORT="5433"
USER="yugabyte"
DATABASE="your_database"

echo "Connecting to YugabyteDB to find topology keys..."
echo ""

# Using ysqlsh
ysqlsh -h "$HOST" -p "$PORT" -U "$USER" -d "$DATABASE" <<EOF
SELECT 
    'Topology Keys: ' || 
    string_agg(DISTINCT cloud || '.' || region || '.' || zone, ',' ORDER BY cloud || '.' || region || '.' || zone) 
    AS topology_keys
FROM yb_servers();
EOF
```

**Or using psql:**

```bash
psql -h "$HOST" -p "$PORT" -U "$USER" -d "$DATABASE" -c "
SELECT 
    'Topology Keys: ' || 
    string_agg(DISTINCT cloud || '.' || region || '.' || zone, ',' ORDER BY cloud || '.' || region || '.' || zone) 
    AS topology_keys
FROM yb_servers();
"
```

---

## Common Topology Key Formats

### Azure:
```
azure.centralus.zone1
azure.eastus.zone1
azure.westus.zone1
```

### AWS:
```
aws.us-east-1.us-east-1a
aws.us-east-1.us-east-1b
aws.us-east-1.us-east-1c
```

### GCP:
```
gcp.us-central1.us-central1-a
gcp.us-central1.us-central1-b
gcp.us-central1.us-central1-c
```

### On-Prem:
```
onprem.datacenter1.rack1
onprem.datacenter1.rack2
onprem.datacenter1.rack3
```

---

## Verification

### After finding topology keys, verify they work:

```sql
-- Test connection with topology keys
-- This should connect to the nearest node based on topology
SELECT 
    inet_server_addr() AS connected_to,
    current_database() AS database;
```

### Enable in Properties File:

```properties
# Enable load balancing with topology keys
spark.cdm.connect.target.yugabyte.loadBalance=true
spark.cdm.connect.target.yugabyte.topologyKeys=azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3
```

### Test Load Balancing:

```bash
# Run migration and check logs
# Should see connections distributed across nodes
grep "loadBalance\|Topology" migration_logs/*.log
```

---

## Troubleshooting

### Issue: `yb_servers()` function not found

**Solution:** Use alternative query:
```sql
SELECT * FROM pg_stat_activity WHERE datname = current_database();
```

### Issue: Cloud/Region/Zone shows as NULL

**Possible Causes:**
1. YugabyteDB not configured with topology
2. Using older YugabyteDB version
3. On-prem deployment without topology labels

**Solution:**
- Check YugabyteDB version: `SELECT version();`
- For on-prem, you may need to set topology manually
- Or use simple format: `onprem.datacenter1.zone1`

### Issue: Multiple regions/zones

**Solution:** Include all regions/zones:
```properties
spark.cdm.connect.target.yugabyte.topologyKeys=azure.centralus.zone1,azure.centralus.zone2,azure.eastus.zone1
```

---

## Quick Reference

**For Azure Central with 3 nodes:**
```properties
spark.cdm.connect.target.yugabyte.loadBalance=true
spark.cdm.connect.target.yugabyte.topologyKeys=azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3
```

**To find your exact values, run:**
```sql
SELECT 
    cloud || '.' || region || '.' || zone AS topology_key
FROM yb_servers()
ORDER BY cloud, region, zone;
```

---

## Next Steps

1. **Find topology keys** using one of the methods above
2. **Update properties file:**
   ```properties
   spark.cdm.connect.target.yugabyte.loadBalance=true
   spark.cdm.connect.target.yugabyte.topologyKeys=your.cloud.region.zone1,your.cloud.region.zone2,your.cloud.region.zone3
   ```
3. **Test migration** and verify load balancing is working
4. **Monitor performance** - should see 1.5-2x improvement

