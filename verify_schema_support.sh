#!/bin/bash

echo "=== Verifying Schema Support Changes ==="
echo ""

echo "1. Checking KnownProperties.java..."
if grep -q "TARGET_YUGABYTE_SCHEMA" src/main/java/com/datastax/cdm/properties/KnownProperties.java; then
    echo "   ✅ TARGET_YUGABYTE_SCHEMA property found"
else
    echo "   ❌ TARGET_YUGABYTE_SCHEMA property NOT found"
fi

echo ""
echo "2. Checking YugabyteSession.java..."
if grep -q "currentSchema=" src/main/java/com/datastax/cdm/yugabyte/YugabyteSession.java; then
    echo "   ✅ currentSchema parameter found"
else
    echo "   ❌ currentSchema parameter NOT found"
fi

echo ""
echo "3. Checking YugabyteTable.java..."
if grep -q "determineSchemaName" src/main/java/com/datastax/cdm/schema/YugabyteTable.java; then
    echo "   ✅ determineSchemaName method found"
else
    echo "   ❌ determineSchemaName method NOT found"
fi

if grep -q "getSchemaName()" src/main/java/com/datastax/cdm/schema/YugabyteTable.java; then
    echo "   ✅ getSchemaName method found"
else
    echo "   ❌ getSchemaName method NOT found"
fi

if grep -q "connection.getCatalog()" src/main/java/com/datastax/cdm/schema/YugabyteTable.java; then
    echo "   ✅ database name detection found"
else
    echo "   ❌ database name detection NOT found"
fi

echo ""
echo "4. Checking YugabyteUpsertStatement.java..."
if grep -q "yugabyteTable.getSchemaName()" src/main/java/com/datastax/cdm/yugabyte/statement/YugabyteUpsertStatement.java; then
    echo "   ✅ getSchemaName() usage found"
else
    echo "   ❌ getSchemaName() usage NOT found"
fi

echo ""
echo "=== Verification Complete ==="
