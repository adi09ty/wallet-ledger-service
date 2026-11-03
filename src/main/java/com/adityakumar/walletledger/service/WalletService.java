package com.adityakumar.walletledger.service;

import com.adityakumar.walletledger.domain.LedgerEntry;
import com.adityakumar.walletledger.domain.Wallet;
import com.adityakumar.walletledger.domain.WalletTransaction;
import com.adityakumar.walletledger.dto.Dtos.CreateWalletRequest;
import com.adityakumar.walletledger.dto.Dtos.LedgerEntryResponse;
import com.adityakumar.walletledger.dto.Dtos.PageResponse;
import com.adityakumar.walletledger.dto.Dtos.ReconciliationResponse;
import com.adityakumar.walletledger.dto.Dtos.TopUpRequest;
import com.adityakumar.walletledger.dto.Dtos.TransactionResponse;
import com.adityakumar.walletledger.dto.Dtos.TransferRequest;
import com.adityakumar.walletledger.dto.Dtos.WalletResponse;
import com.adityakumar.walletledger.exception.InvalidRequestException;
import com.adityakumar.walletledger.exception.TransactionNotFoundException;
import com.adityakumar.walletledger.exception.WalletNotFoundException;
import com.adityakumar.walletledger.repository.LedgerEntryRepository;
import com.adityakumar.walletledger.repository.WalletRepository;
import com.adityakumar.walletledger.repository.WalletTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Entry point for the controllers. Validates input and handles idempotency; the actual money movement is in
 * {@link WalletOperations}. This class is deliberately NOT transactional for top-ups and transfers.
 */
@Service
public class WalletService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final String DEFAULT_CURRENCY = "INR";

    private final WalletOperations operations;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    public WalletService(WalletOperations operations, WalletRepository wallets,
                         WalletTransactionRepository transactions, LedgerEntryRepository ledger, Clock clock) {
        this.operations = operations;
        this.wallets = wallets;
        this.transactions = transactions;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        String currency = request.currency() == null ? DEFAULT_CURRENCY : request.currency();
        Wallet saved = wallets.save(new Wallet(request.ownerName().trim(), currency, LocalDateTime.now(clock)));
        return WalletResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long id) {
        return WalletResponse.from(wallets.findById(id).orElseThrow(() -> new WalletNotFoundException(id)));
    }

    public OperationResult topUp(String idempotencyKey, Long walletId, TopUpRequest request) {
        String key = validateKey(idempotencyKey);
        BigDecimal amount = normalize(request.amount());
        String hash = RequestFingerprint.of("TOPUP", String.valueOf(walletId), amount.toPlainString(),
                nullToEmpty(request.note()));
        try {
            return operations.topUp(key, hash, walletId, amount, request.note());
        } catch (DataIntegrityViolationException duplicateKey) {
            // An identical request committed first. Its transaction is visible now, so return its result.
            return operations.findReplay(key, hash).orElseThrow(() -> duplicateKey);
        }
    }

    public OperationResult transfer(String idempotencyKey, TransferRequest request) {
        String key = validateKey(idempotencyKey);
        if (request.fromWalletId().equals(request.toWalletId())) {
            throw new InvalidRequestException("Cannot transfer to the same wallet");
        }
        BigDecimal amount = normalize(request.amount());
        String hash = RequestFingerprint.of("TRANSFER", String.valueOf(request.fromWalletId()),
                String.valueOf(request.toWalletId()), amount.toPlainString(), nullToEmpty(request.note()));
        try {
            return operations.transfer(key, hash, request.fromWalletId(), request.toWalletId(), amount,
                    request.note());
        } catch (DataIntegrityViolationException duplicateKey) {
            return operations.findReplay(key, hash).orElseThrow(() -> duplicateKey);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> ledger(Long walletId, Pageable pageable) {
        if (!wallets.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        return PageResponse.from(ledger.findByWalletIdOrderByIdDesc(walletId, pageable)
                .map(LedgerEntryResponse::from));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long id) {
        WalletTransaction tx = transactions.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
        List<LedgerEntry> entries = ledger.findByTransactionIdOrderByIdAsc(id);
        return TransactionResponse.from(tx, entries);
    }

    /** Compares the stored balance with the balance re-derived from the ledger. They must always match. */
    @Transactional(readOnly = true)
    public ReconciliationResponse reconcile(Long walletId) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
        BigDecimal net = ledger.netAmountForWallet(walletId);
        BigDecimal ledgerBalance = (net == null ? BigDecimal.ZERO : net).setScale(2, RoundingMode.UNNECESSARY);
        return new ReconciliationResponse(walletId, wallet.getBalance(), ledgerBalance,
                wallet.getBalance().compareTo(ledgerBalance) == 0);
    }

    private static String validateKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidRequestException(
                    "Idempotency-Key must be 1-64 characters: letters, digits, '-' or '_'");
        }
        return key;
    }

    private static BigDecimal normalize(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidRequestException("Amount must have at most 2 decimal places");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
