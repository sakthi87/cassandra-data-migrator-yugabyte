-- Script to generate 100,000 transaction records for performance testing
-- This will test CDM's Phase 1+2 optimizations with realistic data volume

USE transaction_datastore;

-- Clear existing data for clean test
TRUNCATE dda_pstd_fincl_txn_cnsmr_by_accntnbr;

-- Generate 100k records using a Python-like approach via CQL
-- We'll use a simple loop approach with multiple accounts and dates

-- Note: CQL doesn't support loops, so we'll use a batch insert approach
-- For 100k records, we'll generate them programmatically

