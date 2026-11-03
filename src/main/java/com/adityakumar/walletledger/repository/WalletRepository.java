package com.adityakumar.walletledger.repository;

import com.adityakumar.walletledger.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /**
     * Locks the wallet rows (SELECT ... FOR UPDATE) until the transaction ends. Always ordered by id, so two
     * transfers between the same wallets in opposite directions lock in the same order and cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id in :ids order by w.id")
    List<Wallet> lockAllById(@Param("ids") Collection<Long> ids);
}
