package com.adityakumar.walletledger.exception;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String from, String to) {
        super("Cannot transfer between wallets with different currencies: " + from + " and " + to);
    }
}
