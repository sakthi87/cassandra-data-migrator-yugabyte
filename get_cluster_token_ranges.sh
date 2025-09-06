#!/bin/bash
# =====================================================
# CASSANDRA CLUSTER TOKEN RANGE ANALYSIS
# =====================================================

echo "=== CASSANDRA CLUSTER TOKEN RANGE ANALYSIS ==="
echo ""

# 1. Get cluster ring information
echo "1. CLUSTER RING INFORMATION:"
echo "=============================="
nodetool ring

echo ""
echo "2. CLUSTER STATUS:"
echo "=================="
nodetool status

echo ""
echo "3. TABLE INFORMATION:"
echo "===================="
nodetool cfstats your_keyspace.customer

echo ""
echo "4. TOKEN RANGES FOR SPECIFIC NODE:"
echo "=================================="
# Replace 'node_ip' with actual node IP
nodetool getendpoints your_keyspace customer 'some_customer_id'

echo ""
echo "5. SAMPLE TOKEN RANGES:"
echo "======================="
# This will show you the token ranges each node is responsible for
nodetool describering your_keyspace
