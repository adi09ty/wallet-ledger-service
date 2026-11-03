package com.adityakumar.walletledger.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A SHA-256 fingerprint of what a request asks for, so a reused Idempotency-Key can be told apart. */
final class RequestFingerprint {

    private static final char SEPARATOR = '\u0001';

    private RequestFingerprint() {
    }

    static String of(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join(String.valueOf(SEPARATOR), parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
