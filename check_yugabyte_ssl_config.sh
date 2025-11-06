#!/bin/bash
# =============================================================================
# YugabyteDB SSL Configuration Checker
# =============================================================================
# This script tests YSQL and YCQL connections to determine SSL requirements
#
# Usage:
#   ./check_yugabyte_ssl_config.sh [host] [ysql-port] [ycql-port] [username] [password]
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
YSQL_PORT="${2:-5433}"
YCQL_PORT="${3:-9042}"
USERNAME="${4:-yugabyte}"
PASSWORD="${5:-password}"
DATABASE="${6:-yugabyte}"

echo "=========================================="
echo "YugabyteDB SSL Configuration Checker"
echo "=========================================="
echo ""
echo "Host: $HOST"
echo "YSQL Port: $YSQL_PORT"
echo "YCQL Port: $YCQL_PORT"
echo "Username: $USERNAME"
echo ""

# Initialize result variables
YSQL_SSL_OPTIONAL=false
YSQL_SSL_REQUIRED=false
YSQL_FAILED=false
YCQL_SSL_OPTIONAL=false
YCQL_SSL_REQUIRED=false
YCQL_FAILED=false

# =============================================================================
# Test YSQL (PostgreSQL)
# =============================================================================
echo -e "${BLUE}Testing YSQL (PostgreSQL) on port $YSQL_PORT...${NC}"
echo ""

# Test 1: YSQL without SSL
echo -n "  Test 1: Non-SSL connection... "
if timeout 5 psql -h "$HOST" -p "$YSQL_PORT" -U "$USERNAME" -d "$DATABASE" -c "SELECT 1;" >/dev/null 2>&1; then
    echo -e "${GREEN}✓ SUCCESS${NC}"
    YSQL_SSL_OPTIONAL=true
else
    echo -e "${RED}✗ FAILED${NC}"
    
    # Test 2: YSQL with SSL required
    echo -n "  Test 2: SSL connection (required mode)... "
    if timeout 5 psql "host=$HOST port=$YSQL_PORT user=$USERNAME dbname=$DATABASE sslmode=require" -c "SELECT 1;" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ SUCCESS${NC}"
        YSQL_SSL_REQUIRED=true
    else
        echo -e "${RED}✗ FAILED${NC}"
        YSQL_FAILED=true
    fi
fi

echo ""

# =============================================================================
# Test YCQL (Cassandra)
# =============================================================================
echo -e "${BLUE}Testing YCQL (Cassandra) on port $YCQL_PORT...${NC}"
echo ""

# Check if cqlsh is available
if ! command -v cqlsh &> /dev/null; then
    echo -e "${YELLOW}⚠ cqlsh not found. Skipping YCQL tests.${NC}"
    echo "  Install with: pip install cqlsh"
    YCQL_FAILED=true
else
    # Test 1: YCQL without SSL
    echo -n "  Test 1: Non-SSL connection... "
    if timeout 5 cqlsh "$HOST" "$YCQL_PORT" -u "$USERNAME" -p "$PASSWORD" -e "SELECT release_version FROM system.local;" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ SUCCESS${NC}"
        YCQL_SSL_OPTIONAL=true
    else
        echo -e "${RED}✗ FAILED${NC}"
        
        # Test 2: YCQL with SSL
        echo -n "  Test 2: SSL connection... "
        if timeout 5 cqlsh "$HOST" "$YCQL_PORT" -u "$USERNAME" -p "$PASSWORD" --ssl -e "SELECT release_version FROM system.local;" >/dev/null 2>&1; then
            echo -e "${GREEN}✓ SUCCESS${NC}"
            YCQL_SSL_REQUIRED=true
        else
            echo -e "${RED}✗ FAILED${NC}"
            echo "  Note: SSL may require certificate. Try:"
            echo "    cqlsh $HOST $YCQL_PORT -u $USERNAME -p $PASSWORD --ssl --certfile=/path/to/ca.crt"
            YCQL_FAILED=true
        fi
    fi
fi

echo ""
echo "=========================================="
echo "Results Summary"
echo "=========================================="
echo ""

# YSQL Results
echo -e "${BLUE}YSQL (PostgreSQL) Configuration:${NC}"
if [ "$YSQL_SSL_OPTIONAL" = true ]; then
    echo -e "  ${GREEN}✓ SSL is OPTIONAL (accepts both SSL and non-SSL)${NC}"
    echo "  → Current CDM code will work (hardcoded ssl=false)"
    echo "  → Server accepts non-SSL connections"
elif [ "$YSQL_SSL_REQUIRED" = true ]; then
    echo -e "  ${YELLOW}⚠ SSL is REQUIRED (only accepts SSL)${NC}"
    echo "  → Current CDM code will FAIL (hardcoded ssl=false)"
    echo "  → Need to modify YugabyteSession.java to enable SSL"
elif [ "$YSQL_FAILED" = true ]; then
    echo -e "  ${RED}✗ Connection test failed${NC}"
    echo "  → Check network, credentials, or YugabyteDB service"
else
    echo -e "  ${YELLOW}? Could not determine (psql not found or connection failed)${NC}"
fi

echo ""

# YCQL Results
echo -e "${BLUE}YCQL (Cassandra) Configuration:${NC}"
if [ "$YCQL_SSL_OPTIONAL" = true ]; then
    echo -e "  ${GREEN}✓ SSL is OPTIONAL (accepts both SSL and non-SSL)${NC}"
    echo "  → Current CDM code should work (SSL disabled by default)"
    echo "  → Server accepts non-SSL connections"
elif [ "$YCQL_SSL_REQUIRED" = true ]; then
    echo -e "  ${YELLOW}⚠ SSL is REQUIRED (only accepts SSL)${NC}"
    echo "  → Current CDM code will FAIL (SSL disabled by default)"
    echo "  → Need to enable SSL in properties file with truststore"
    echo ""
    echo "  Solution:"
    echo "    1. Create truststore: ./create_truststore.sh ca.crt YourPassword"
    echo "    2. Update properties file:"
    echo "       spark.cdm.connect.target.tls.enabled=true"
    echo "       spark.cdm.connect.target.tls.trustStore.path=/path/to/truststore.jks"
    echo "       spark.cdm.connect.target.tls.trustStore.password=YourPassword"
elif [ "$YCQL_FAILED" = true ]; then
    echo -e "  ${RED}✗ Connection test failed or cqlsh not available${NC}"
    echo "  → Install cqlsh: pip install cqlsh"
    echo "  → Or check network/credentials"
else
    echo -e "  ${YELLOW}? Could not determine${NC}"
fi

echo ""
echo "=========================================="
echo "Configuration Matrix"
echo "=========================================="
echo ""

if [ "$YSQL_SSL_OPTIONAL" = true ] && [ "$YCQL_SSL_REQUIRED" = true ]; then
    echo -e "${YELLOW}Your Configuration (Most Common):${NC}"
    echo "  YSQL: SSL optional → Current code works ✓"
    echo "  YCQL: SSL required → Need to enable SSL in properties"
    echo ""
    echo "This matches your situation!"
elif [ "$YSQL_SSL_OPTIONAL" = true ] && [ "$YCQL_SSL_OPTIONAL" = true ]; then
    echo -e "${GREEN}Your Configuration:${NC}"
    echo "  YSQL: SSL optional → Current code works ✓"
    echo "  YCQL: SSL optional → Current code works ✓"
elif [ "$YSQL_SSL_REQUIRED" = true ] || [ "$YCQL_SSL_REQUIRED" = true ]; then
    echo -e "${RED}Your Configuration:${NC}"
    echo "  One or both protocols require SSL"
    echo "  → Need to enable SSL in CDM configuration"
fi

echo ""
echo "=========================================="
echo "Next Steps"
echo "=========================================="
echo ""

if [ "$YCQL_SSL_REQUIRED" = true ]; then
    echo "1. Get root certificate from YugabyteDB:"
    echo "   scp user@yugabyte-node:/opt/yugabyte/tls/certs/ca.crt ./ca.crt"
    echo ""
    echo "2. Create truststore:"
    echo "   ./create_truststore.sh ca.crt YourPassword"
    echo ""
    echo "3. Update cassandra-to-ycql-migration.properties"
    echo ""
    echo "4. See YUGABYTE_YCQL_SSL_SETUP.md for detailed instructions"
fi

if [ "$YSQL_SSL_REQUIRED" = true ]; then
    echo "⚠️  YSQL requires SSL but code has it hardcoded to disabled"
    echo "   Need to modify YugabyteSession.java to enable SSL"
fi

echo ""
echo "For detailed configuration check, see: CHECK_YUGABYTEDB_SSL_CONFIG.md"
echo ""

