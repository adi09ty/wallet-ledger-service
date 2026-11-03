package com.adityakumar.walletledger.repository;

import com.adityakumar.walletledger.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);
}
