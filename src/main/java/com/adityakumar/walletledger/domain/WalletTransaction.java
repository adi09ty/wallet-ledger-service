package com.adityakumar.walletledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One completed money movement (a top-up or a transfer). Written once and never changed.
 * The unique idempotency key is what stops a retried request from being applied twice,
 * even when two identical requests arrive at the same moment.
 */
@Entity
@Immutable
@Table(name = "wallet_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_tx_idempotency_key", columnNames = "idempotency_key"))
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    /** Null for a top-up (the money comes from outside the system). */
    @Column(name = "from_wallet_id")
    private Long fromWalletId;

    @Column(name = "to_wallet_id", nullable = false)
    private Long toWalletId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String note;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /** Fingerprint of the request, used to detect a key being reused for a different request. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected WalletTransaction() {
        // required by JPA
    }

    public WalletTransaction(TransactionType type, Long fromWalletId, Long toWalletId, BigDecimal amount,
                             String currency, String note, String idempotencyKey, String requestHash,
                             LocalDateTime createdAt) {
        this.type = type;
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        this.amount = amount;
        this.currency = currency;
        this.note = note;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public Long getFromWalletId() {
        return fromWalletId;
    }

    public Long getToWalletId() {
        return toWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNote() {
        return note;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
