package com.adityakumar.walletledger.domain;

import com.adityakumar.walletledger.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletTest {

    private Wallet wallet() {
        return new Wallet("Asha", "INR", LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void newWalletStartsAtZero() {
        assertEquals(0, wallet().getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void creditAndDebitChangeTheBalance() {
        Wallet wallet = wallet();
        wallet.credit(money("100.50"));
        wallet.debit(money("40.25"));
        assertEquals(money("60.25"), wallet.getBalance());
    }

    @Test
    void canSpendTheExactBalance() {
        Wallet wallet = wallet();
        wallet.credit(money("10.00"));
        wallet.debit(money("10.00"));
        assertEquals(0, wallet.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void cannotOverdrawAndTheBalanceIsLeftUntouched() {
        Wallet wallet = wallet();
        wallet.credit(money("10.00"));
        assertThrows(InsufficientFundsException.class, () -> wallet.debit(money("10.01")));
        assertEquals(money("10.00"), wallet.getBalance());
    }

    @Test
    void decimalMathIsExact() {
        Wallet wallet = wallet();
        for (int i = 0; i < 10; i++) {
            wallet.credit(money("0.10"));
        }
        assertEquals(money("1.00"), wallet.getBalance());
    }
}
