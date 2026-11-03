package com.adityakumar.walletledger.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long walletId, BigDecimal balance, BigDecimal requested) {
        super("Wallet " + walletId + " has " + balance + " but " + requested + " was requested");
    }
}
