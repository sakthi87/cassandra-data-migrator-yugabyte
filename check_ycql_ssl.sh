#!/bin/bash
# =============================================================================
# Yugabyte YCQL SSL Requirement Checker
# =============================================================================
# This script helps determine if SSL is required for YCQL connections
#
# Usage:
#   ./check_ycql_ssl.sh [host] [port] [username] [password]
# =============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values or from parameters
HOST="${1:-vcausc11udev057.azr.bank-dns.com}"
PORT="${2:-9042}"
USERNAME="${3:-yugabyte}"
PASSWORD="${4:-password}"

echo "=========================================="
echo "Yugabyte YCQL SSL Requirement Checker"
echo "=========================================="
echo ""
echo "Testing connection to: $HOST:$PORT"
echo "Username: $USERNAME"
echo ""

# Check if cqlsh is available
if ! command -v cqlsh &> /dev/null; then
    echo -e "${RED}Error: cqlsh not found. Please install Cassandra tools.${NC}"
    echo ""
    echo "You can install it via:"
    echo "  pip install cqlsh"
    echo ""
    echo "Or test manually with:"
    echo "  cqlsh $HOST $PORT -u $USERNAME -p $PASSWORD"
    echo "  cqlsh $HOST $PORT -u $USERNAME -p $PASSWORD --ssl"
    exit 1
fi

# Test 1: Connection without SSL
echo -e "${BLUE}Test 1: Testing YCQL connection WITHOUT SSL...${NC}"
if timeout 10 cqlsh "$HOST" "$PORT" -u "$USERNAME" -p "$PASSWORD" -e "SELECT release_version FROM system.local;" 2>/dev/null; then
    echo -e "${GREEN}✓ SUCCESS: YCQL connection works WITHOUT SSL${NC}"
    echo ""
    echo -e "${GREEN}Result: SSL is NOT required for YCQL${NC}"
    echo ""
    echo "Your properties file should have SSL disabled (default):"
    echo "  # spark.cdm.connect.target.tls.enabled=false  (commented out = disabled)"
    NO_SSL_SUCCESS=true
else
    echo -e "${RED}✗ FAILED: Connection without SSL failed${NC}"
    NO_SSL_SUCCESS=false
fi

echo ""

# Test 2: Connection with SSL
echo -e "${BLUE}Test 2: Testing YCQL connection WITH SSL...${NC}"
if timeout 10 cqlsh "$HOST" "$PORT" -u "$USERNAME" -p "$PASSWORD" --ssl -e "SELECT release_version FROM system.local;" 2>/dev/null; then
    echo -e "${GREEN}✓ SUCCESS: YCQL connection works WITH SSL${NC}"
    SSL_SUCCESS=true
else
    echo -e "${RED}✗ FAILED: Connection with SSL failed${NC}"
    echo ""
    echo "Note: If SSL is required, you may need to provide certificate:"
    echo "  cqlsh $HOST $PORT -u $USERNAME -p $PASSWORD --ssl --certfile=/path/to/ca.crt"
    SSL_SUCCESS=false
fi

echo ""
echo "=========================================="
echo "Summary"
echo "=========================================="
echo ""

if [ "$NO_SSL_SUCCESS" = true ]; then
    echo -e "${GREEN}✓ SSL is NOT required for YCQL${NC}"
    echo ""
    echo "Your CDM configuration should have SSL disabled (default):"
    echo "  # Leave SSL properties commented out"
    echo "  # spark.cdm.connect.target.tls.enabled=false"
    echo ""
    echo "If you're still getting SSL errors, check:"
    echo "  1. Network/firewall issues"
    echo "  2. Credentials are correct"
    echo "  3. Hostname/port is correct"
elif [ "$SSL_SUCCESS" = true ]; then
    echo -e "${YELLOW}⚠ SSL IS REQUIRED for YCQL${NC}"
    echo ""
    echo "You need to enable SSL in your CDM properties file:"
    echo ""
    echo "  1. Create truststore.jks:"
    echo "     ./create_truststore.sh ca.crt YourPassword"
    echo ""
    echo "  2. Update cassandra-to-ycql-migration.properties:"
    echo "     spark.cdm.connect.target.tls.enabled=true"
    echo "     spark.cdm.connect.target.tls.trustStore.path=/path/to/truststore.jks"
    echo "     spark.cdm.connect.target.tls.trustStore.password=YourPassword"
    echo "     spark.cdm.connect.target.tls.trustStore.type=JKS"
    echo ""
    echo "  See YUGABYTE_YCQL_SSL_SETUP.md for detailed instructions"
else
    echo -e "${RED}✗ Both SSL and non-SSL connections failed${NC}"
    echo ""
    echo "Possible issues:"
    echo "  1. Network/firewall blocking port $PORT"
    echo "  2. Incorrect credentials"
    echo "  3. YugabyteDB YCQL service not running"
    echo "  4. Hostname/IP incorrect"
    echo ""
    echo "Troubleshooting:"
    echo "  # Test network connectivity"
    echo "  ping $HOST"
    echo "  nc -zv $HOST $PORT"
    echo ""
    echo "  # Test with different credentials"
    echo "  cqlsh $HOST $PORT -u <username> -p <password>"
fi

echo ""
echo "=========================================="
echo "Why YSQL Worked But YCQL Doesn't"
echo "=========================================="
echo ""
echo "YSQL (PostgreSQL, port 5433):"
echo "  - SSL is HARDCODED to disabled in code"
echo "  - Works even if YugabyteDB requires SSL"
echo ""
echo "YCQL (Cassandra, port 9042):"
echo "  - SSL is configurable via properties"
echo "  - If YugabyteDB requires SSL, you must enable it"
echo ""
echo "See YSQL_VS_YCQL_SSL_EXPLANATION.md for details"
echo ""

