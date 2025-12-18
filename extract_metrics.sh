#!/bin/bash
# Extract key performance metrics from migration summary

SUMMARY_FILE=$(ls -t migration_logs/migration_summary_*.txt 2>/dev/null | head -1)

if [ -z "$SUMMARY_FILE" ]; then
    echo "❌ No migration summary file found in migration_logs/"
    echo "   Make sure migration has been run at least once"
    exit 1
fi

echo "📊 Migration Performance Metrics"
echo "=================================="
echo "Summary File: $SUMMARY_FILE"
echo ""

# Extract key metrics
echo "⏱️  TIMING:"
grep "Total Migration Duration" "$SUMMARY_FILE" | sed 's/^/   /'
grep "Migration Start Time" "$SUMMARY_FILE" | sed 's/^/   /'
grep "Migration End Time" "$SUMMARY_FILE" | sed 's/^/   /'
echo ""

echo "📈 RECORDS:"
grep "Total Records Read" "$SUMMARY_FILE" | sed 's/^/   /'
grep "Total Records Written" "$SUMMARY_FILE" | sed 's/^/   /'
grep "Total Records Failed" "$SUMMARY_FILE" | sed 's/^/   /'
echo ""

echo "🚀 PERFORMANCE:"
grep "Average Throughput" "$SUMMARY_FILE" | sed 's/^/   /'
grep "Peak Throughput" "$SUMMARY_FILE" | sed 's/^/   /'
echo ""

echo "✅ QUALITY:"
grep "Success Rate" "$SUMMARY_FILE" | sed 's/^/   /'
grep "Error Rate" "$SUMMARY_FILE" | sed 's/^/   /'
echo ""

# Calculate additional metrics if possible
THROUGHPUT=$(grep "Average Throughput" "$SUMMARY_FILE" | grep -oE '[0-9]+\.[0-9]+' | head -1)
RECORDS=$(grep "Total Records Written" "$SUMMARY_FILE" | grep -oE '[0-9]+' | head -1)

if [ ! -z "$THROUGHPUT" ] && [ ! -z "$RECORDS" ]; then
    echo "📊 CALCULATED METRICS:"
    RECORDS_PER_HOUR=$(echo "$THROUGHPUT * 3600" | bc 2>/dev/null || echo "$THROUGHPUT * 3600" | awk '{print $1 * 3600}')
    echo "   Records per hour: $RECORDS_PER_HOUR"
    
    # Estimate for 6.4M records
    if [ ! -z "$THROUGHPUT" ]; then
        EST_SECONDS=$(echo "scale=0; 6400000 / $THROUGHPUT" | bc 2>/dev/null || echo "scale=0; 6400000 / $THROUGHPUT" | awk '{printf "%.0f", 6400000/$1}')
        EST_MINUTES=$(echo "scale=1; $EST_SECONDS / 60" | bc 2>/dev/null || echo "scale=1; $EST_SECONDS / 60" | awk '{printf "%.1f", $1/60}')
        echo "   Estimated time for 6.4M records: ~$EST_MINUTES minutes"
    fi
fi

echo ""
echo "📄 Full summary available in: $SUMMARY_FILE"
