-- ==========================================================================
-- CASSANDRA TO YUGABYTEDB POSTGRESQL TABLE CONVERSION
-- ==========================================================================
-- Original Cassandra Table: nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid
-- Converted to YugabyteDB YSQL (PostgreSQL) Table

-- ==========================================================================
-- YUGABYTEDB YSQL TABLE CREATION
-- ==========================================================================

-- Create schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS nbb_transaction_datastore;

-- Create the table in YugabyteDB YSQL
CREATE TABLE nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid (
    -- Primary Key Columns (Partition Key + Clustering Key)
    accnt_uid TEXT NOT NULL,
    prdct_cde TEXT NOT NULL,
    pstd_dt DATE NOT NULL,
    txn_uid TEXT NOT NULL,
    
    -- Account Information
    accnt_bal DOUBLE PRECISION,
    accnt_nbr TEXT,
    bal_id_nbr INTEGER,
    crdac_id TEXT,
    cshadv_indctr TEXT,
    ctry_cde TEXT,
    doc_id TEXT,
    
    -- Enriched Category Information
    enrchd_ctgry_guid TEXT,
    enrchd_ctgry_is_incm TEXT,
    enrchd_ctgry_nm TEXT,
    enrchd_ctgry_prnt_guid TEXT,
    enrchd_desc TEXT,
    enrchd_is_bill_pay TEXT,
    enrchd_is_dirdep TEXT,
    enrchd_is_expns TEXT,
    enrchd_is_fee TEXT,
    enrchd_is_incm TEXT,
    enrchd_is_od_fee TEXT,
    enrchd_is_pyrl_advnc TEXT,
    enrchd_is_subscrptn_srvc_pmt_indctr TEXT,
    
    -- Merchant Information
    enrchd_merch_guid TEXT,
    enrchd_merch_loc_city TEXT,
    enrchd_merch_loc_ctry TEXT,
    enrchd_merch_loc_guid TEXT,
    enrchd_merch_loc_lat TEXT,
    enrchd_merch_loc_long TEXT,
    enrchd_merch_loc_merch_guid TEXT,
    enrchd_merch_loc_phn_nbr TEXT,
    enrchd_merch_loc_pstl_cde TEXT,
    enrchd_merch_loc_st_addr TEXT,
    enrchd_merch_loc_ste TEXT,
    enrchd_merch_loc_store_nbr TEXT,
    enrchd_merch_logo_url TEXT,
    enrchd_merch_nm TEXT,
    enrchd_merch_website_url TEXT,
    enrchd_sub_ctgry_nm TEXT,
    enrchd_usr_repl_mx_ctgry_guid TEXT,
    
    -- Transaction Amounts
    escrw_amt DOUBLE PRECISION,
    fee_amt DOUBLE PRECISION,
    folio_nbr TEXT,
    from_accnt TEXT,
    from_routng TEXT,
    frst_nm TEXT,
    instlmnt_nbr TEXT,
    int_amt DOUBLE PRECISION,
    intrnl_card_id TEXT,
    intrnl_chitl_txt TEXT,
    
    -- Transaction Indicators
    is_disptd_indctr TEXT,
    is_disputable_indctr TEXT,
    is_instlmnt_txn TEXT,
    
    -- ITID Information
    itid_applctn_sys_cde TEXT,
    itid_orgntn_cde TEXT,
    itid_ts_txt TEXT,
    last_4_pan TEXT,
    last_4_rfrnc_nbr TEXT,
    last_nm TEXT,
    load_ts TIMESTAMP,
    
    -- Level 3 Data
    lvl_3_date DATE,
    lvl_3_dta1 TEXT,
    lvl_3_dta2 TEXT,
    lvl_3_indctr TEXT,
    lvl_3_nm TEXT,
    
    -- Merchant Details
    merch_cde TEXT,
    merch_city_nm TEXT,
    merch_ctgry_cde TEXT,
    merch_ctgry_cde_desc TEXT,
    merch_ctry_cde TEXT,
    merch_ctry_nm TEXT,
    merch_desc TEXT,
    merch_id TEXT,
    merch_nm TEXT,
    merch_pstl_cde TEXT,
    merch_ste_cde TEXT,
    
    -- Currency Information
    orig_crncy_amt DOUBLE PRECISION,
    orig_crncy_cde TEXT,
    orig_crncy_iso_cde TEXT,
    orig_crncy_nm TEXT,
    
    -- Payment System Information
    pmt_sys TEXT,
    pnt_of_sale_ts TIMESTAMP,
    pos_dy_of_wk_txt TEXT,
    pos_entry_mode_txt TEXT,
    prchs_authntctn_mthd TEXT,
    prncpl_amt DOUBLE PRECISION,
    pstd_ts TIMESTAMP,
    pymt_conf_nbr TEXT,
    rec_typ TEXT,
    recurr_indctn TEXT,
    ref_nbr TEXT,
    rtn TEXT,
    seq_nbr TEXT,
    solr_query TEXT,
    src_card_nbr TEXT,
    src_file_nm TEXT,
    sub_prdct_cde TEXT,
    tax_amt DOUBLE PRECISION,
    token_indctr TEXT,
    token_rqstr_nm TEXT,
    
    -- Transaction Details
    txn_amt DOUBLE PRECISION,
    txn_cde TEXT,
    txn_crncy_cde TEXT,
    txn_crncy_iso_cde TEXT,
    txn_desc TEXT,
    txn_drctn TEXT,
    txn_hash_key TEXT,
    txn_id TEXT,
    txn_src_nm TEXT,
    txn_status TEXT,
    txn_ts TIMESTAMP,
    txn_typ TEXT,
    txn_typ_ctgry_cde TEXT,
    txn_typ_shrt_desc TEXT,
    
    -- Audit Information
    z_audit_crtd_by_txt TEXT,
    z_audit_crtd_ts TIMESTAMP,
    z_audit_evnt_id TEXT,
    z_audit_last_mdfd_by_txt TEXT,
    
    -- Primary Key (Composite)
    PRIMARY KEY (accnt_uid, prdct_cde, pstd_dt, txn_uid)
);

-- ==========================================================================
-- CREATE INDEXES FOR BETTER PERFORMANCE
-- ==========================================================================

-- Index on account number for lookups
CREATE INDEX idx_ccd_pstd_fincl_txn_accnt_nbr 
ON nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid (accnt_nbr);

-- Index on transaction ID for lookups
CREATE INDEX idx_ccd_pstd_fincl_txn_txn_id 
ON nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid (txn_id);

-- Index on posted timestamp for time-based queries
CREATE INDEX idx_ccd_pstd_fincl_txn_pstd_ts 
ON nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid (pstd_ts);

-- Index on transaction timestamp for time-based queries
CREATE INDEX idx_ccd_pstd_fincl_txn_txn_ts 
ON nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid (txn_ts);

-- Index on merchant ID for merchant-based queries
CREATE INDEX idx_ccd_pstd_fincl_txn_merch_id 
ON nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid (merch_id);

-- Index on transaction amount for amount-based queries
CREATE INDEX idx_ccd_pstd_fincl_txn_txn_amt 
ON nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid (txn_amt);

-- ==========================================================================
-- DATA TYPE MAPPING REFERENCE
-- ==========================================================================
/*
CASSANDRA -> POSTGRESQL MAPPING:
- text -> TEXT
- date -> DATE  
- double -> DOUBLE PRECISION
- int -> INTEGER
- timestamp -> TIMESTAMP
- Primary Key: ((accnt_uid, prdct_cde), pstd_dt, txn_uid) -> (accnt_uid, prdct_cde, pstd_dt, txn_uid)
- Clustering Order: (pstd_dt DESC, txn_uid ASC) -> Handled by PRIMARY KEY ordering
*/

-- ==========================================================================
-- VERIFICATION QUERIES
-- ==========================================================================

-- Check table structure
\d nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid;

-- Check indexes
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'ccd_pstd_fincl_txn_cnsmr_by_accntuid' 
AND schemaname = 'nbb_transaction_datastore';

-- Count rows (after migration)
SELECT COUNT(*) FROM nbb_transaction_datastore.ccd_pstd_fincl_txn_cnsmr_by_accntuid;
