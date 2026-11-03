package com.adityakumar.walletledger.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used for a different request");
    }
}
