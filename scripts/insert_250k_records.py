#!/usr/bin/env python3
"""
Generate 250,000 test transaction records for Cassandra
This script creates realistic transaction data for performance testing
"""

from cassandra.cluster import Cluster
from cassandra.query import BatchStatement, ConsistencyLevel
import random
import uuid
from datetime import datetime, timedelta
import time

# Connection settings
CASSANDRA_HOST = 'localhost'
CASSANDRA_PORT = 9043
KEYSPACE = 'transaction_datastore'
TABLE = 'dda_pstd_fincl_txn_cnsmr_by_accntnbr'

# Test data parameters
TOTAL_RECORDS = 250000
BATCH_SIZE = 100  # Insert in batches of 100
ACCOUNTS_PER_COMPANY = 50  # 50 accounts per company
COMPANIES = ['COMP001', 'COMP002', 'COMP003', 'COMP004', 'COMP005']
PRODUCTS = ['PRD001', 'PRD002', 'PRD003']
MERCHANTS = ['Walmart', 'Amazon', 'Shell', 'Starbucks', 'CVS', 'Whole Foods', 'Target', 'Best Buy']
TRANSACTION_TYPES = ['DEBIT', 'CREDIT']
TRANSACTION_STATUSES = ['COMPLETED', 'PENDING', 'FAILED']

def generate_transaction_data(company_id, account_num, product_code, posted_date, txn_seq):
    """Generate a single transaction record"""
    txn_amt = round(random.uniform(-1000, 1000), 2)
    txn_type = 'CREDIT' if txn_amt < 0 else 'DEBIT'
    
    return {
        'cmpny_id': company_id,
        'accnt_nbr': account_num,
        'prdct_cde': product_code,
        'pstd_dt': posted_date,
        'txn_seq': txn_seq,
        'accnt_id': f'ACC_ID_{account_num}',
        'accnt_nbr_hash': f'HASH{account_num}',
        'accnt_bal': round(random.uniform(1000, 10000), 2),
        'avail_bal': round(random.uniform(500, 9500), 2),
        'txn_amt': txn_amt,
        'taxbl_pmt_amt': abs(txn_amt) if txn_amt > 0 else 0,
        'tot_pmt_amt': abs(txn_amt),
        'prncpl_amt': abs(txn_amt),
        'txn_id': f'TXN{random.randint(100000, 999999)}',
        'txn_uid': f'UID{random.randint(100000, 999999)}',
        'txn_cde': f'TXN_CODE_{random.randint(1, 100)}',
        'txn_typ': txn_type,
        'txn_status': random.choice(TRANSACTION_STATUSES),
        'txn_desc': random.choice(MERCHANTS),
        'txn_drctn': txn_type,
        'txn_crncy_cde': 'USD',
        'pstd_ts': datetime.now(),
        'txn_ts': datetime.now(),
        'pnt_of_sale_ts': datetime.now(),
        'sub_prdct_cde': f'SUB_PRD_{random.randint(1, 10)}',
        'src_nm': f'SOURCE_{random.randint(1, 10)}',
        'ref_nbr': f'REF{random.randint(100000, 999999)}',
        'enrchd_merch_nm': random.choice(MERCHANTS),
        'enrchd_merch_loc_city': random.choice(['San Francisco', 'Oakland', 'Palo Alto', 'Berkeley', 'San Jose']),
        'enrchd_merch_loc_ste': 'CA',
        'z_audit_crtd_by_txt': 'SYSTEM',
        'z_audit_evnt_id': f'EVT{random.randint(100000, 999999)}',
        'z_audit_crtd_ts': datetime.now()
    }

def main():
    print(f"Connecting to Cassandra at {CASSANDRA_HOST}:{CASSANDRA_PORT}")
    cluster = Cluster([CASSANDRA_HOST], port=CASSANDRA_PORT)
    session = cluster.connect(KEYSPACE)
    
    # Prepare insert statement
    insert_stmt = session.prepare(f"""
        INSERT INTO {TABLE} (
            cmpny_id, accnt_nbr, prdct_cde, pstd_dt, txn_seq,
            accnt_id, accnt_nbr_hash, accnt_bal, avail_bal,
            txn_amt, taxbl_pmt_amt, tot_pmt_amt, prncpl_amt,
            txn_id, txn_uid, txn_cde, txn_typ, txn_status, txn_desc, txn_drctn,
            txn_crncy_cde, pstd_ts, txn_ts, pnt_of_sale_ts,
            sub_prdct_cde, src_nm, ref_nbr,
            enrchd_merch_nm, enrchd_merch_loc_city, enrchd_merch_loc_ste,
            z_audit_crtd_by_txt, z_audit_evnt_id, z_audit_crtd_ts
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """)
    
    print(f"Generating {TOTAL_RECORDS} records...")
    start_time = time.time()
    
    record_count = 0
    batch = BatchStatement(consistency_level=ConsistencyLevel.ONE)
    
    # Generate records across multiple companies, accounts, and dates
    base_date = datetime(2024, 12, 1)
    
    # Calculate distribution to generate exactly TOTAL_RECORDS
    records_per_account = TOTAL_RECORDS // (len(COMPANIES) * ACCOUNTS_PER_COMPANY)
    if records_per_account == 0:
        records_per_account = 1
    
    for company_idx, company_id in enumerate(COMPANIES):
        for account_idx in range(ACCOUNTS_PER_COMPANY):
            account_num = f'ACC{company_idx * ACCOUNTS_PER_COMPANY + account_idx + 1:04d}'
            product_code = random.choice(PRODUCTS)
            
            # Generate transactions for this account
            for txn_idx in range(records_per_account):
                if record_count >= TOTAL_RECORDS:
                    break
                
                # Distribute across dates (30 days)
                day_offset = txn_idx % 30
                posted_date = base_date + timedelta(days=day_offset)
                date_str = posted_date.strftime('%Y-%m-%d')
                
                # Generate unique transaction sequence
                txn_seq = f'SEQ{txn_idx + 1:06d}'
                data = generate_transaction_data(company_id, account_num, product_code, date_str, txn_seq)
                
                batch.add(insert_stmt, (
                    data['cmpny_id'], data['accnt_nbr'], data['prdct_cde'], data['pstd_dt'], data['txn_seq'],
                    data['accnt_id'], data['accnt_nbr_hash'], data['accnt_bal'], data['avail_bal'],
                    data['txn_amt'], data['taxbl_pmt_amt'], data['tot_pmt_amt'], data['prncpl_amt'],
                    data['txn_id'], data['txn_uid'], data['txn_cde'], data['txn_typ'], data['txn_status'],
                    data['txn_desc'], data['txn_drctn'], data['txn_crncy_cde'], data['pstd_ts'],
                    data['txn_ts'], data['pnt_of_sale_ts'], data['sub_prdct_cde'], data['src_nm'],
                    data['ref_nbr'], data['enrchd_merch_nm'], data['enrchd_merch_loc_city'],
                    data['enrchd_merch_loc_ste'], data['z_audit_crtd_by_txt'], data['z_audit_evnt_id'],
                    data['z_audit_crtd_ts']
                ))
                
                record_count += 1
                
                # Execute batch when it reaches batch size
                if len(batch) >= BATCH_SIZE:
                    session.execute(batch)
                    batch = BatchStatement(consistency_level=ConsistencyLevel.ONE)
                    if record_count % 10000 == 0:
                        elapsed = time.time() - start_time
                        rate = record_count / elapsed
                        print(f"  Inserted {record_count:,} records ({rate:.0f} records/sec)")
            
            if record_count >= TOTAL_RECORDS:
                break
        if record_count >= TOTAL_RECORDS:
            break
    
    # Generate remaining records if needed
    while record_count < TOTAL_RECORDS:
        company_id = random.choice(COMPANIES)
        account_num = f'ACC{random.randint(1, len(COMPANIES) * ACCOUNTS_PER_COMPANY):04d}'
        product_code = random.choice(PRODUCTS)
        day_offset = random.randint(0, 29)
        posted_date = base_date + timedelta(days=day_offset)
        date_str = posted_date.strftime('%Y-%m-%d')
        txn_seq = f'SEQ{record_count + 1:06d}'
        
        data = generate_transaction_data(company_id, account_num, product_code, date_str, txn_seq)
        batch.add(insert_stmt, (
            data['cmpny_id'], data['accnt_nbr'], data['prdct_cde'], data['pstd_dt'], data['txn_seq'],
            data['accnt_id'], data['accnt_nbr_hash'], data['accnt_bal'], data['avail_bal'],
            data['txn_amt'], data['taxbl_pmt_amt'], data['tot_pmt_amt'], data['prncpl_amt'],
            data['txn_id'], data['txn_uid'], data['txn_cde'], data['txn_typ'], data['txn_status'],
            data['txn_desc'], data['txn_drctn'], data['txn_crncy_cde'], data['pstd_ts'],
            data['txn_ts'], data['pnt_of_sale_ts'], data['sub_prdct_cde'], data['src_nm'],
            data['ref_nbr'], data['enrchd_merch_nm'], data['enrchd_merch_loc_city'],
            data['enrchd_merch_loc_ste'], data['z_audit_crtd_by_txt'], data['z_audit_evnt_id'],
            data['z_audit_crtd_ts']
        ))
        
        record_count += 1
        
        if len(batch) >= BATCH_SIZE:
            session.execute(batch)
            batch = BatchStatement(consistency_level=ConsistencyLevel.ONE)
            if record_count % 10000 == 0:
                elapsed = time.time() - start_time
                rate = record_count / elapsed
                print(f"  Inserted {record_count:,} records ({rate:.0f} records/sec)")
    
    # Execute remaining batch
    if len(batch) > 0:
        session.execute(batch)
    
    elapsed_time = time.time() - start_time
    rate = record_count / elapsed_time
    
    print(f"\n✅ Successfully inserted {record_count:,} records")
    print(f"   Time: {elapsed_time:.2f} seconds")
    print(f"   Rate: {rate:.0f} records/second")
    
    # Verify count
    result = session.execute(f"SELECT COUNT(*) FROM {TABLE}")
    count = result.one()[0]
    print(f"\n✅ Verified: {count:,} records in table")
    
    session.shutdown()
    cluster.shutdown()

if __name__ == '__main__':
    main()

