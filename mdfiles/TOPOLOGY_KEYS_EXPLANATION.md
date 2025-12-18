# TopologyKeys and LoadBalance Explanation

## What is the Error?

The error **"Malformed topology-keys property value"** occurs when:
- `loadBalance=true` is set in the YugabyteDB Smart Driver configuration
- `topologyKeys` is empty or not provided

## What are TopologyKeys?

**TopologyKeys** in YugabyteDB Smart Driver specify which **cloud regions and zones** the driver should prefer for load balancing. They follow this format:

```
cloud.region.zone
```

**Examples:**
- `aws.us-east-1.us-east-1a` - AWS US East 1, zone A
- `azure.eastus.eastus-1` - Azure East US, zone 1
- `gcp.us-central1.us-central1-a` - GCP US Central 1, zone A

### Why are they needed?

When `loadBalance=true`, the YugabyteDB Smart Driver needs to know:
1. **Which nodes** to balance connections across
2. **Which regions/zones** to prefer (for geo-location awareness)
3. **How to route** queries to the closest nodes

Without `topologyKeys`, the driver cannot determine this information and throws an error.

## When to Use Load Balancing

### ✅ **Enable Load Balancing When:**
- **Multi-region deployment**: YugabyteDB cluster spans multiple cloud regions
- **Geo-distributed**: Nodes in different geographic locations
- **High availability**: Need automatic failover and connection distribution
- **Example**: Production cluster with nodes in `aws.us-east-1` and `aws.us-west-2`

**Configuration:**
```properties
spark.cdm.connect.target.yugabyte.loadBalance=true
spark.cdm.connect.target.yugabyte.topologyKeys=aws.us-east-1.us-east-1a,aws.us-west-2.us-west-2a
```

### ❌ **Disable Load Balancing When:**
- **Local Docker**: Single-node or local development setup
- **Single region**: All nodes in the same region/zone
- **Simple setup**: No need for geo-aware routing
- **Example**: Local Docker container for testing

**Configuration:**
```properties
spark.cdm.connect.target.yugabyte.loadBalance=false
# topologyKeys not needed
```

## How dsbulk Handles This

### dsbulk's Approach (Cassandra Driver)

**dsbulk uses the DataStax Java Driver** (for Cassandra), which has a different approach:

1. **Automatic Inference**: Uses `DcInferringLoadBalancingPolicy`
   - Automatically infers the local data center from contact points
   - No explicit topology keys required
   - Simpler configuration

2. **Contact Points**: Only needs host addresses
   ```bash
   dsbulk load -h host1,host2,host3 ...
   ```

3. **No Topology Keys**: Doesn't use topology keys at all
   - Works well for Cassandra's simpler topology model
   - Data center inference is sufficient

### Why CDM is Different (YugabyteDB Smart Driver)

**CDM uses YugabyteDB Smart Driver** (for YSQL), which is designed for:

1. **Multi-Cloud Deployments**: Supports AWS, Azure, GCP simultaneously
2. **Geo-Location Awareness**: Routes queries to closest nodes
3. **Advanced Topology**: More complex than Cassandra's data center model

**Requirements:**
- When `loadBalance=true`: **MUST** provide `topologyKeys`
- When `loadBalance=false`: Works without topology keys (single node/local)

## Current CDM Implementation

### ✅ **Fixed Behavior:**

The code now **only enables load balancing if topologyKeys are provided**:

```java
String topologyKeys = propertyHelper.getString(KnownProperties.TARGET_YUGABYTE_TOPOLOGY_KEYS);
Boolean loadBalance = propertyHelper.getBoolean(KnownProperties.TARGET_YUGABYTE_LOAD_BALANCE);

if (loadBalance != null && loadBalance && topologyKeys != null && !topologyKeys.trim().isEmpty()) {
    // Enable load balancing with topology keys
    urlParams.add("loadBalance=true");
    urlParams.add("topologyKeys=" + topologyKeys);
} else if (loadBalance != null && loadBalance) {
    // Warn and disable load balancing if topologyKeys not provided
    logger.warn("loadBalance requested but topologyKeys not provided - disabling for local/single-node setup");
}
```

### Configuration Examples

#### Example 1: Local Docker (Current Test Setup)
```properties
# Disable load balancing for local Docker
spark.cdm.connect.target.yugabyte.loadBalance=false
# topologyKeys not needed
```

#### Example 2: Multi-Region Production
```properties
# Enable load balancing with topology keys
spark.cdm.connect.target.yugabyte.loadBalance=true
spark.cdm.connect.target.yugabyte.topologyKeys=aws.us-east-1.us-east-1a,aws.us-west-2.us-west-2a
```

#### Example 3: Single Region, Multiple Zones
```properties
# Enable load balancing within single region
spark.cdm.connect.target.yugabyte.loadBalance=true
spark.cdm.connect.target.yugabyte.topologyKeys=aws.us-east-1.us-east-1a,aws.us-east-1.us-east-1b
```

## Key Differences: dsbulk vs CDM

| Feature | dsbulk (Cassandra Driver) | CDM (YugabyteDB Smart Driver) |
|---------|---------------------------|-------------------------------|
| **Load Balancing** | Automatic (infers from contact points) | Requires topology keys when enabled |
| **Topology Keys** | Not used | Required when `loadBalance=true` |
| **Configuration** | Simple (just hosts) | More complex (needs topology info) |
| **Use Case** | Cassandra clusters | YugabyteDB multi-region deployments |
| **Local Testing** | Works out of the box | Must disable load balancing |

## Summary

1. **TopologyKeys** = Cloud region/zone identifiers for geo-aware load balancing
2. **loadBalance=true** requires **topologyKeys** to be set (YugabyteDB Smart Driver requirement)
3. **For local Docker**: Set `loadBalance=false` (no topology keys needed)
4. **For production**: Set `loadBalance=true` and provide `topologyKeys` with your region/zone info
5. **dsbulk** doesn't have this issue because it uses a different driver with automatic inference

The current CDM implementation now handles this correctly by:
- ✅ Only enabling load balancing when topology keys are provided
- ✅ Warning and disabling load balancing for local setups
- ✅ Working correctly for both local and production scenarios

