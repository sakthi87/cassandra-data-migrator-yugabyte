-- =====================================================
-- CASSANDRA TOKEN RANGE ANALYSIS FOR CUSTOM MIGRATION
-- =====================================================

-- 1. Get overall token distribution for your customer table
SELECT 
  MIN(TOKEN(customer_id)) as min_token,
  MAX(TOKEN(customer_id)) as max_token,
  COUNT(*) as total_rows,
  COUNT(DISTINCT TOKEN(customer_id)) as unique_tokens
FROM customer;

-- 2. Get token distribution by ranges (useful for splitting)
SELECT 
  TOKEN(customer_id) as token_value,
  COUNT(*) as row_count
FROM customer 
GROUP BY TOKEN(customer_id)
ORDER BY token_value
LIMIT 100;

-- 3. Get token ranges with row counts (for custom partitioning)
SELECT 
  TOKEN(customer_id) as token_value,
  customer_id,
  COUNT(*) as row_count
FROM customer 
GROUP BY TOKEN(customer_id), customer_id
ORDER BY token_value
LIMIT 50;

-- 4. Sample token ranges for testing (first 1000 rows)
SELECT 
  TOKEN(customer_id) as token_value,
  customer_id
FROM customer 
LIMIT 1000;

-- 5. Get token ranges for specific customer IDs (if you know some)
-- SELECT 
--   TOKEN(customer_id) as token_value,
--   customer_id
-- FROM customer 
-- WHERE customer_id IN ('customer1', 'customer2', 'customer3');
