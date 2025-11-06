#!/bin/bash
# =============================================================================
# Quick Script to Create Truststore for Yugabyte YCQL
# =============================================================================
# This script helps you quickly create a truststore.jks file for Yugabyte YCQL SSL connections
#
# Usage:
#   ./create_truststore.sh [certificate-file] [truststore-password]
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
CERT_FILE="${1:-ca.crt}"
TRUSTSTORE_PASSWORD="${2}"
TRUSTSTORE_NAME="truststore.jks"
ALIAS_NAME="yugabyte-root-ca"

echo "=========================================="
echo "Yugabyte YCQL Truststore Creator"
echo "=========================================="
echo ""

# Check if keytool is available
if ! command -v keytool &> /dev/null; then
    echo -e "${RED}Error: keytool not found. Please install Java JDK.${NC}"
    exit 1
fi

# Check if certificate file exists
if [ ! -f "$CERT_FILE" ]; then
    echo -e "${RED}Error: Certificate file '$CERT_FILE' not found!${NC}"
    echo ""
    echo "Usage: $0 [certificate-file] [truststore-password]"
    echo ""
    echo "Example:"
    echo "  $0 ca.crt MyPassword123!"
    echo ""
    echo "To get the certificate:"
    echo "  scp user@yugabyte-node:/opt/yugabyte/tls/certs/ca.crt ./ca.crt"
    exit 1
fi

# Prompt for password if not provided
if [ -z "$TRUSTSTORE_PASSWORD" ]; then
    echo -e "${YELLOW}Enter password for truststore (will not be displayed):${NC}"
    read -s TRUSTSTORE_PASSWORD
    echo ""
    
    if [ -z "$TRUSTSTORE_PASSWORD" ]; then
        echo -e "${RED}Error: Password cannot be empty!${NC}"
        exit 1
    fi
    
    echo -e "${YELLOW}Confirm password:${NC}"
    read -s PASSWORD_CONFIRM
    echo ""
    
    if [ "$TRUSTSTORE_PASSWORD" != "$PASSWORD_CONFIRM" ]; then
        echo -e "${RED}Error: Passwords do not match!${NC}"
        exit 1
    fi
fi

# Check if truststore already exists
if [ -f "$TRUSTSTORE_NAME" ]; then
    echo -e "${YELLOW}Warning: $TRUSTSTORE_NAME already exists!${NC}"
    read -p "Do you want to overwrite it? (y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 1
    fi
    rm -f "$TRUSTSTORE_NAME"
fi

# Create truststore
echo ""
echo -e "${GREEN}Creating truststore...${NC}"
echo "Certificate file: $CERT_FILE"
echo "Truststore file: $TRUSTSTORE_NAME"
echo "Alias: $ALIAS_NAME"
echo ""

keytool -importcert \
    -alias "$ALIAS_NAME" \
    -file "$CERT_FILE" \
    -keystore "$TRUSTSTORE_NAME" \
    -storetype JKS \
    -storepass "$TRUSTSTORE_PASSWORD" \
    -noprompt

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ Truststore created successfully!${NC}"
    echo ""
    
    # Verify truststore
    echo "Verifying truststore..."
    keytool -list -v -keystore "$TRUSTSTORE_NAME" -storepass "$TRUSTSTORE_PASSWORD" | grep -A 5 "Alias name: $ALIAS_NAME" || true
    
    echo ""
    echo "=========================================="
    echo -e "${GREEN}Next Steps:${NC}"
    echo "=========================================="
    echo ""
    echo "1. Update your properties file with:"
    echo ""
    echo "   spark.cdm.connect.target.tls.enabled=true"
    echo "   spark.cdm.connect.target.tls.trustStore.path=$(pwd)/$TRUSTSTORE_NAME"
    echo "   spark.cdm.connect.target.tls.trustStore.password=$TRUSTSTORE_PASSWORD"
    echo "   spark.cdm.connect.target.tls.trustStore.type=JKS"
    echo ""
    echo "2. Set appropriate permissions:"
    echo "   chmod 600 $TRUSTSTORE_NAME"
    echo ""
    echo "3. Test connection with cqlsh:"
    echo "   cqlsh your-yugabyte-host 9042 -u username -p password --ssl --certfile=$CERT_FILE"
    echo ""
    echo "For detailed instructions, see: YUGABYTE_YCQL_SSL_SETUP.md"
    echo ""
    
    # Set permissions
    chmod 600 "$TRUSTSTORE_NAME"
    echo -e "${GREEN}✓ Permissions set to 600 (read/write for owner only)${NC}"
    echo ""
else
    echo ""
    echo -e "${RED}✗ Failed to create truststore!${NC}"
    exit 1
fi

