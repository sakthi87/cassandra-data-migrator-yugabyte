#!/usr/bin/env python3
"""
Cassandra Token Range Analysis Script
=====================================
This script helps you analyze token ranges in your Cassandra table
to determine optimal custom token ranges for migration.
"""

from cassandra.cluster import Cluster
from cassandra.auth import PlainTextAuthProvider
import sys
import math

def connect_to_cassandra(host, port, username, password, keyspace):
    """Connect to Cassandra cluster"""
    auth_provider = PlainTextAuthProvider(username=username, password=password)
    cluster = Cluster([host], port=port, auth_provider=auth_provider)
    session = cluster.connect(keyspace)
    return session, cluster

def analyze_token_ranges(session, table_name, partition_key):
    """Analyze token ranges in the table"""
    
    print(f"=== ANALYZING TOKEN RANGES FOR {table_name} ===")
    print()
    
    # 1. Get overall statistics
    query = f"""
    SELECT 
        MIN(TOKEN({partition_key})) as min_token,
        MAX(TOKEN({partition_key})) as max_token,
        COUNT(*) as total_rows,
        COUNT(DISTINCT TOKEN({partition_key})) as unique_tokens
    FROM {table_name}
    """
    
    result = session.execute(query).one()
    min_token = result.min_token
    max_token = result.max_token
    total_rows = result.total_rows
    unique_tokens = result.unique_tokens
    
    print(f"📊 OVERALL STATISTICS:")
    print(f"   Total Rows: {total_rows:,}")
    print(f"   Unique Tokens: {unique_tokens:,}")
    print(f"   Min Token: {min_token}")
    print(f"   Max Token: {max_token}")
    print(f"   Token Range: {max_token - min_token:,}")
    print()
    
    # 2. Get token distribution
    query = f"""
    SELECT 
        TOKEN({partition_key}) as token_value,
        COUNT(*) as row_count
    FROM {table_name}
    GROUP BY TOKEN({partition_key})
    ORDER BY token_value
    LIMIT 100
    """
    
    results = session.execute(query)
    print(f"📈 TOKEN DISTRIBUTION (First 100 tokens):")
    print(f"   {'Token Value':<20} {'Row Count':<10}")
    print(f"   {'-'*20} {'-'*10}")
    
    for row in results:
        print(f"   {row.token_value:<20} {row.row_count:<10,}")
    
    print()
    
    # 3. Calculate optimal ranges for different split counts
    token_range = max_token - min_token
    print(f"🎯 OPTIMAL TOKEN RANGES FOR DIFFERENT SPLITS:")
    print()
    
    for num_splits in [4, 8, 16, 32, 64]:
        range_size = token_range // num_splits
        print(f"   {num_splits} splits (range size: {range_size:,}):")
        
        for i in range(num_splits):
            start_token = min_token + (i * range_size)
            end_token = min_token + ((i + 1) * range_size) - 1
            if i == num_splits - 1:  # Last range gets remaining tokens
                end_token = max_token
            
            print(f"     Range {i+1}: {start_token} to {end_token}")
        print()

def main():
    """Main function"""
    if len(sys.argv) != 6:
        print("Usage: python3 analyze_token_ranges.py <host> <port> <username> <password> <keyspace>")
        print("Example: python3 analyze_token_ranges.py localhost 9042 cassandra cassandra my_keyspace")
        sys.exit(1)
    
    host = sys.argv[1]
    port = int(sys.argv[2])
    username = sys.argv[3]
    password = sys.argv[4]
    keyspace = sys.argv[5]
    
    try:
        session, cluster = connect_to_cassandra(host, port, username, password, keyspace)
        
        # Analyze customer table
        analyze_token_ranges(session, "customer", "customer_id")
        
        cluster.shutdown()
        
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
