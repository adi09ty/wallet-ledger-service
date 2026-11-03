package com.adityakumar.walletledger.repository;

import com.adityakumar.walletledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

    List<LedgerEntry> findByTransactionIdOrderByIdAsc(Long transactionId);

    /** Credits minus debits for one wallet; null when the wallet has no entries yet. */
    @Query("""
            select sum(case when e.type = com.adityakumar.walletledger.domain.EntryType.CREDIT
                            then e.amount else -e.amount end)
            from LedgerEntry e
            where e.walletId = :walletId
            """)
    BigDecimal netAmountForWallet(@Param("walletId") Long walletId);
}
