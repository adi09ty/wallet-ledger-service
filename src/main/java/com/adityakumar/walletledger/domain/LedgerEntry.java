package com.adityakumar.walletledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line in a wallet's history. Append-only: entries are never updated or deleted, so the ledger
 * is a permanent audit trail and a wallet's balance can always be re-derived from it.
 */
@Entity
@Immutable
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_wallet", columnList = "wallet_id, id"),
        @Index(name = "idx_ledger_transaction", columnList = "transaction_id")})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private EntryType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** The wallet balance right after this entry was applied. */
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected LedgerEntry() {
        // required by JPA
    }

    public LedgerEntry(Long transactionId, Long walletId, EntryType type, BigDecimal amount,
                       BigDecimal balanceAfter, LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public EntryType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
