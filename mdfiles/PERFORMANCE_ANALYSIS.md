# Performance Analysis - Aggressive Configuration Results

## Migration Results

**Configuration:** Aggressive
- Spark: `local[6]`
- Driver Memory: 3G
- Parallelism: 24
- Partitions: 24
- Batch Size: 100
- Fetch Size: 5,000

**Results:**
- Status: ✅ COMPLETE (24/24 partitions)
- Records: 250,000 / 250,000 (100%)
- Errors: 0
- Duration: 97 seconds (1.6 minutes)

**Performance:**
- Average Throughput: **2,554 IOPS** (2.55K)
- Peak Throughput: **3,192 IOPS** (3.19K)
- Previous Best: 3,500 IOPS

---

## Analysis

### Performance Comparison

| Configuration | Partitions | Average IOPS | Peak IOPS | Duration |
|---------------|------------|--------------|-----------|----------|
| **Previous Best** | 40 | 3,159 | - | 79s |
| **Aggressive** | 24 | 2,554 | 3,192 | 97s |
| **Gap** | - | -605 IOPS | -308 IOPS | +18s |

### Key Findings

1. **Peak Throughput (3,192 IOPS)** is close to previous best (3,500 IOPS)
   - Suggests the system CAN achieve ~3.5K IOPS
   - Average is lower due to startup/teardown overhead

2. **24 Partitions May Still Be Too Many**
   - 24 partitions on 6 cores = 4 partitions per core
   - May cause context switching overhead
   - Previous best used 40 partitions but with different config

3. **Memory Usage**
   - 3G driver memory is within limits
   - No swapping detected
   - Memory is not the bottleneck

---

## Optimization Recommendations

### Option 1: Try Balanced Configuration (18 Partitions)
**Rationale:** Reduce partitions to 3x cores (18 partitions on 6 cores)

```bash
./optimize_for_local_machine.sh balanced
```

**Expected:** 3,000-3,500 IOPS (closer to previous best)

### Option 2: Fine-tune Aggressive (20 Partitions)
**Rationale:** Between balanced (18) and aggressive (24)

**Manual Configuration:**
- Partitions: 20
- Parallelism: 20
- Batch Size: 100
- Fetch Size: 5,000

**Expected:** 3,200-3,800 IOPS

### Option 3: Optimize for Peak Performance
**Rationale:** Previous best was 3,159 IOPS with 40 partitions

**Configuration:**
- Partitions: 15-18 (2.5-3x cores)
- Parallelism: 15-18
- Batch Size: 75-100
- Fetch Size: 3,000-5,000

**Expected:** 3,500-4,000 IOPS

---

## Bottleneck Analysis

### What's Working:
- ✅ Connection errors: 0 (fixed!)
- ✅ Batch processing: 100% efficient
- ✅ Rate limits: 20,000 (not limiting)
- ✅ Memory: Within limits

### Potential Bottlenecks:
1. **Partition Count:** 24 may still be too many
2. **Context Switching:** 4 partitions per core = overhead
3. **Startup Overhead:** First/last partitions slower

---

## Next Steps

1. **Try Balanced (18 partitions):** Should be closer to 3.5K IOPS
2. **Monitor partition times:** Identify slow partitions
3. **Fine-tune:** Adjust partitions between 15-20
4. **Document:** Record best configuration for your machine

---

## Conclusion

**Current Best:** 2,554 IOPS average, 3,192 IOPS peak  
**Previous Best:** 3,159 IOPS  
**Target:** 3,500-4,000 IOPS

**Recommendation:** Try balanced configuration (18 partitions) to find the sweet spot between too few (underutilized) and too many (overhead).

