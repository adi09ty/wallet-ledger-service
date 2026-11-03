package com.adityakumar.walletledger.dto;

import com.adityakumar.walletledger.domain.EntryType;
import com.adityakumar.walletledger.domain.LedgerEntry;
import com.adityakumar.walletledger.domain.TransactionType;
import com.adityakumar.walletledger.domain.Wallet;
import com.adityakumar.walletledger.domain.WalletTransaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request and response shapes of the API, grouped in one file. Entities are never returned directly. */
public final class Dtos {

    private Dtos() {
    }

    public record CreateWalletRequest(
            @NotBlank @Size(max = 100) String ownerName,
            @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter uppercase currency code, e.g. INR") String currency) {
    }

    public record TopUpRequest(
            @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
            @Digits(integer = 12, fraction = 2, message = "must have at most 12 digits and 2 decimals") BigDecimal amount,
            @Size(max = 255) String note) {
    }

    public record TransferRequest(
            @NotNull Long fromWalletId,
            @NotNull Long toWalletId,
            @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
            @Digits(integer = 12, fraction = 2, message = "must have at most 12 digits and 2 decimals") BigDecimal amount,
            @Size(max = 255) String note) {
    }

    public record WalletResponse(Long id, String ownerName, String currency, BigDecimal balance,
                                 LocalDateTime createdAt) {
        public static WalletResponse from(Wallet wallet) {
            return new WalletResponse(wallet.getId(), wallet.getOwnerName(), wallet.getCurrency(),
                    wallet.getBalance(), wallet.getCreatedAt());
        }
    }

    public record LedgerEntryResponse(Long id, Long transactionId, Long walletId, EntryType type,
                                      BigDecimal amount, BigDecimal balanceAfter, LocalDateTime createdAt) {
        public static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(entry.getId(), entry.getTransactionId(), entry.getWalletId(),
                    entry.getType(), entry.getAmount(), entry.getBalanceAfter(), entry.getCreatedAt());
        }
    }

    public record TransactionResponse(Long id, TransactionType type, Long fromWalletId, Long toWalletId,
                                      BigDecimal amount, String currency, String note, LocalDateTime createdAt,
                                      List<LedgerEntryResponse> entries) {
        public static TransactionResponse from(WalletTransaction tx, List<LedgerEntry> entries) {
            return new TransactionResponse(tx.getId(), tx.getType(), tx.getFromWalletId(), tx.getToWalletId(),
                    tx.getAmount(), tx.getCurrency(), tx.getNote(), tx.getCreatedAt(),
                    entries.stream().map(LedgerEntryResponse::from).toList());
        }
    }

    public record ReconciliationResponse(Long walletId, BigDecimal balance, BigDecimal ledgerBalance,
                                         boolean consistent) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
