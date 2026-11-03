package com.adityakumar.walletledger.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RequestFingerprintTest {

    @Test
    void sameRequestGivesTheSameFingerprint() {
        assertEquals(RequestFingerprint.of("TOPUP", "1", "10.00", ""),
                RequestFingerprint.of("TOPUP", "1", "10.00", ""));
    }

    @Test
    void anyDifferenceChangesTheFingerprint() {
        String base = RequestFingerprint.of("TOPUP", "1", "10.00", "");
        assertNotEquals(base, RequestFingerprint.of("TOPUP", "2", "10.00", ""));
        assertNotEquals(base, RequestFingerprint.of("TOPUP", "1", "10.01", ""));
        assertNotEquals(base, RequestFingerprint.of("TOPUP", "1", "10.00", "note"));
        assertNotEquals(base, RequestFingerprint.of("TRANSFER", "1", "10.00", ""));
    }

    @Test
    void partBoundariesCannotBeFaked() {
        assertNotEquals(RequestFingerprint.of("ab", "c"), RequestFingerprint.of("a", "bc"));
    }

    @Test
    void fingerprintIsA64CharacterHexString() {
        assertEquals(64, RequestFingerprint.of("x").length());
    }
}
