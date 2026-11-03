package com.adityakumar.walletledger.domain;

import com.adityakumar.walletledger.exception.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A balance held in one currency. The balance is only changed through credit() and debit(). */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Safety net on top of the row locks: a concurrent unlocked update fails instead of overwriting. */
    @Version
    private Long version;

    protected Wallet() {
        // required by JPA
    }

    public Wallet(String ownerName, String currency, LocalDateTime createdAt) {
        this.ownerName = ownerName;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    /** The balance can never go below zero. */
    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        balance = balance.subtract(amount);
    }

    public Long getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
