# Load Balance and Connection Pool Analysis

## Current Settings Analysis

### 1. Load Balance Setting (Line 55)

**Current:**
```properties
spark.cdm.connect.target.yugabyte.loadBalance=false
```

**Your Environment:**
- YugabyteDB: Azure Central with **3 nodes** (one in each zone)
- Current: Single region (Azure Central)
- Future: Multi-region deployment

**Analysis:**

#### For Current Setup (Single Region, 3 Nodes):
- **loadBalance=false is OK** for now, but **not optimal**
- With 3 nodes, load balancing would distribute connections across all 3 nodes
- This could improve throughput by utilizing all nodes

#### For Multi-Region (Future):
- **loadBalance=true is REQUIRED** for optimal performance
- Distributes connections across regions based on topology
- Reduces latency by connecting to nearest region

**Recommendation:**

**Option A: Enable Now (Recommended for 3-node cluster)**
```properties
# Enable load balancing for 3-node Azure Central cluster
spark.cdm.connect.target.yugabyte.loadBalance=true
# Topology keys for Azure Central (3 zones)
# Format: cloud.region.zone (e.g., azure.centralus.zone1)
spark.cdm.connect.target.yugabyte.topologyKeys=azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3
```

**Option B: Keep Disabled (If topology keys unknown)**
```properties
# Keep disabled if you don't know exact topology keys
# Will work but won't utilize all 3 nodes optimally
spark.cdm.connect.target.yugabyte.loadBalance=false
```

**How to Find Topology Keys:**
```sql
-- Connect to YugabyteDB and run:
SELECT cloud, region, zone FROM pg_stat_activity LIMIT 1;
-- Or check YugabyteDB admin UI for node topology
```

**Impact on Performance:**
- **With loadBalance=false:** All connections go to one node (or round-robin at driver level)
- **With loadBalance=true:** Connections distributed across all 3 nodes = **3x better utilization**

---

### 2. Connection Pool Settings (Lines 63-64)

**Current:**
```properties
spark.cdm.connect.target.yugabyte.pool.maxSize=5
spark.cdm.connect.target.yugabyte.pool.minSize=2
```

**Your Configuration:**
- `numParts=40` (40 Spark partitions)
- Each partition creates its own `YugabyteSession` → its own connection pool
- **Total potential connections:** 40 partitions × 5 maxSize = **200 connections**

**Analysis:**

#### Current Formula:
```
pool.maxSize = numParts / 8 = 40 / 8 = 5 ✅ (matches current setting)
```

#### Is 5 Enough?

**Per Partition:**
- Each partition processes data sequentially (within that partition)
- 5 connections per partition allows:
  - 1 active write
  - 4 queued/prepared
  - This is **sufficient** for sequential processing

**Total Connections:**
- 40 partitions × 5 = 200 max connections
- YugabyteDB default max connections: **100-300** (depends on configuration)
- **Risk:** Could hit connection limits if all partitions are active simultaneously

**Recommendation:**

**Option A: Keep Current (Conservative)**
```properties
# Current setting - safe for most deployments
spark.cdm.connect.target.yugabyte.pool.maxSize=5
spark.cdm.connect.target.yugabyte.pool.minSize=2
```
- **Pros:** Won't overwhelm YugabyteDB
- **Cons:** May limit throughput if YugabyteDB can handle more

**Option B: Increase for Higher Throughput (Aggressive)**
```properties
# Increase for maximum throughput (if YugabyteDB can handle it)
spark.cdm.connect.target.yugabyte.pool.maxSize=3
spark.cdm.connect.target.yugabyte.pool.minSize=1
```
- **Total:** 40 × 3 = 120 connections (safer)
- **Pros:** Less risk of connection exhaustion
- **Cons:** Slightly less parallelism per partition

**Option C: Dynamic Based on YugabyteDB Capacity**
```properties
# If YugabyteDB max_connections = 300, and you have 40 partitions:
# Safe: 300 / 40 = 7.5 → use 7
# Conservative: 300 / 40 / 2 = 3.75 → use 3
spark.cdm.connect.target.yugabyte.pool.maxSize=3
spark.cdm.connect.target.yugabyte.pool.minSize=1
```

**For Multi-Region:**
```properties
# Multi-region needs more connections for load balancing
spark.cdm.connect.target.yugabyte.pool.maxSize=10
spark.cdm.connect.target.yugabyte.pool.minSize=2
```

---

## Recommended Configuration

### For Current Setup (Single Region, 3 Nodes):

```properties
# =============================================================================
# HIGH-PERFORMANCE SETTINGS (Phase 1+2 Optimizations)
# =============================================================================
spark.cdm.connect.target.yugabyte.batchSize=50
spark.cdm.connect.target.yugabyte.rewriteBatchedInserts=true

# Load Balancing - ENABLE for 3-node cluster
# Uncomment and set topology keys when known
# spark.cdm.connect.target.yugabyte.loadBalance=true
# spark.cdm.connect.target.yugabyte.topologyKeys=azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3

# For now, keep disabled if topology keys unknown
spark.cdm.connect.target.yugabyte.loadBalance=false

spark.cdm.connect.target.yugabyte.prepareThreshold=5
spark.cdm.connect.target.yugabyte.socketTimeout=60000
spark.cdm.connect.target.yugabyte.tcpKeepAlive=true

# Connection Pool Settings
# Conservative: 3 connections per partition (40 × 3 = 120 total)
# This is safer and still provides good performance
spark.cdm.connect.target.yugabyte.pool.maxSize=3
spark.cdm.connect.target.yugabyte.pool.minSize=1
```

### For Multi-Region Setup (Future):

```properties
# Load Balancing - REQUIRED for multi-region
spark.cdm.connect.target.yugabyte.loadBalance=true
spark.cdm.connect.target.yugabyte.topologyKeys=azure.centralus.zone1,azure.eastus.zone1,azure.westus.zone1

# Connection Pool - Increase for multi-region
spark.cdm.connect.target.yugabyte.pool.maxSize=10
spark.cdm.connect.target.yugabyte.pool.minSize=2
```

---

## Performance Impact

### Load Balance Impact:

**Without Load Balancing (current):**
- All connections to one node (or simple round-robin)
- **Throughput:** Limited by single node capacity
- **Utilization:** ~33% (1 of 3 nodes)

**With Load Balancing:**
- Connections distributed across all 3 nodes
- **Throughput:** Up to 3x improvement (if network allows)
- **Utilization:** ~100% (all 3 nodes)

**Expected Improvement:** 1.5-2x throughput increase

### Connection Pool Impact:

**Current (maxSize=5):**
- 200 total connections (40 × 5)
- Risk of connection exhaustion
- Good parallelism per partition

**Recommended (maxSize=3):**
- 120 total connections (40 × 3)
- Safer, less risk
- Still good parallelism

**Expected Impact:** Minimal (3-5% difference), but safer

---

## Action Items

1. **Find Topology Keys:**
   ```sql
   -- Connect to YugabyteDB
   -- Check node topology in admin UI or via SQL
   ```

2. **Enable Load Balancing (if topology keys known):**
   ```properties
   spark.cdm.connect.target.yugabyte.loadBalance=true
   spark.cdm.connect.target.yugabyte.topologyKeys=azure.centralus.zone1,azure.centralus.zone2,azure.centralus.zone3
   ```

3. **Adjust Connection Pool (recommended):**
   ```properties
   spark.cdm.connect.target.yugabyte.pool.maxSize=3  # Safer: 120 total connections
   spark.cdm.connect.target.yugabyte.pool.minSize=1
   ```

4. **Test and Monitor:**
   - Check YugabyteDB connection count: `SELECT count(*) FROM pg_stat_activity;`
   - Monitor for "too many clients" errors
   - Check throughput improvement

---

## Summary

| Setting | Current | Recommended | Impact |
|---------|---------|-------------|--------|
| **loadBalance** | `false` | `true` (if topology keys known) | +50-100% throughput |
| **topologyKeys** | Not set | Set for 3 zones | Required for loadBalance |
| **pool.maxSize** | `5` | `3` (safer) | Prevents connection exhaustion |
| **pool.minSize** | `2` | `1` (matches maxSize) | Reduces idle connections |

**Priority:**
1. **High:** Reduce `pool.maxSize` to 3 (safety)
2. **Medium:** Enable `loadBalance` with topology keys (performance)
3. **Low:** Adjust `pool.minSize` to 1 (efficiency)

