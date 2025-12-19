#!/bin/bash
# Script to find YugabyteDB topology keys
# Usage: ./find_topology_keys.sh [host] [port] [user] [database]

# Default values
HOST="${1:-localhost}"
PORT="${2:-5433}"
USER="${3:-yugabyte}"
DATABASE="${4:-yugabyte}"

echo "=========================================="
echo "Finding YugabyteDB Topology Keys"
echo "=========================================="
echo "Host: $HOST"
echo "Port: $PORT"
echo "User: $USER"
echo "Database: $DATABASE"
echo ""

# Check if ysqlsh is available
if command -v ysqlsh &> /dev/null; then
    echo "Using ysqlsh..."
    echo ""
    
    ysqlsh -h "$HOST" -p "$PORT" -U "$USER" -d "$DATABASE" <<EOF
-- Method 1: Query yb_servers() function
SELECT 
    'Method 1: yb_servers() function' AS method,
    cloud,
    region,
    zone,
    cloud || '.' || region || '.' || zone AS topology_key
FROM yb_servers()
ORDER BY cloud, region, zone;

-- Method 2: Get formatted topology keys
SELECT 
    'Topology Keys (comma-separated):' AS label,
    string_agg(
        DISTINCT cloud || '.' || region || '.' || zone, 
        ',' 
        ORDER BY cloud || '.' || region || '.' || zone
    ) AS topology_keys
FROM yb_servers();

-- Method 3: Check pg_stat_activity
SELECT 
    'Method 2: pg_stat_activity' AS method,
    DISTINCT cloud,
    region,
    zone
FROM pg_stat_activity
WHERE datname = current_database()
LIMIT 10;
EOF

elif command -v psql &> /dev/null; then
    echo "Using psql..."
    echo ""
    
    PGPASSWORD="${PGPASSWORD:-yugabyte}" psql -h "$HOST" -p "$PORT" -U "$USER" -d "$DATABASE" <<EOF
-- Method 1: Query yb_servers() function
SELECT 
    'Method 1: yb_servers() function' AS method,
    cloud,
    region,
    zone,
    cloud || '.' || region || '.' || zone AS topology_key
FROM yb_servers()
ORDER BY cloud, region, zone;

-- Method 2: Get formatted topology keys
SELECT 
    'Topology Keys (comma-separated):' AS label,
    string_agg(
        DISTINCT cloud || '.' || region || '.' || zone, 
        ',' 
        ORDER BY cloud || '.' || region || '.' || zone
    ) AS topology_keys
FROM yb_servers();
EOF

else
    echo "ERROR: Neither ysqlsh nor psql found!"
    echo ""
    echo "Please install one of the following:"
    echo "  1. YugabyteDB ysqlsh (recommended)"
    echo "  2. PostgreSQL psql client"
    echo ""
    echo "Or connect manually and run:"
    echo "  SELECT cloud || '.' || region || '.' || zone AS topology_key"
    echo "  FROM yb_servers()"
    echo "  ORDER BY cloud, region, zone;"
    exit 1
fi

echo ""
echo "=========================================="
echo "Next Steps:"
echo "=========================================="
echo "1. Copy the topology keys from above"
echo "2. Update transaction-test-audit.properties:"
echo "   spark.cdm.connect.target.yugabyte.loadBalance=true"
echo "   spark.cdm.connect.target.yugabyte.topologyKeys=<paste-keys-here>"
echo "3. Restart migration to enable load balancing"
echo ""

