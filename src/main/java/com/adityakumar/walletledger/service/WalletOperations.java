package com.adityakumar.walletledger.service;

import com.adityakumar.walletledger.domain.EntryType;
import com.adityakumar.walletledger.domain.LedgerEntry;
import com.adityakumar.walletledger.domain.TransactionType;
import com.adityakumar.walletledger.domain.Wallet;
import com.adityakumar.walletledger.domain.WalletTransaction;
import com.adityakumar.walletledger.exception.CurrencyMismatchException;
import com.adityakumar.walletledger.exception.IdempotencyConflictException;
import com.adityakumar.walletledger.exception.WalletNotFoundException;
import com.adityakumar.walletledger.repository.LedgerEntryRepository;
import com.adityakumar.walletledger.repository.WalletRepository;
import com.adityakumar.walletledger.repository.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The money-moving core. Every public method runs in ONE database transaction, so a top-up or transfer
 * either happens completely (balances, transaction row, ledger entries) or not at all.
 *
 * Kept separate from {@link WalletService} so that service can catch a duplicate-key failure
 * AFTER this transaction has rolled back, which is how concurrent identical requests are resolved.
 */
@Service
public class WalletOperations {

    /** Structured audit log, separate from the application log so it can be routed on its own. */
    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    public WalletOperations(WalletRepository wallets, WalletTransactionRepository transactions,
                            LedgerEntryRepository ledger, Clock clock) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    public OperationResult topUp(String idempotencyKey, String requestHash, Long walletId, BigDecimal amount,
                                 String note) {
        Optional<OperationResult> earlier = findReplay(idempotencyKey, requestHash);
        if (earlier.isPresent()) {
            return earlier.get();
        }

        Wallet wallet = lockWallets(List.of(walletId)).get(walletId);
        LocalDateTime now = LocalDateTime.now(clock);

        wallet.credit(amount);
        // The unique idempotency key makes this insert fail if an identical request won a race with us.
        WalletTransaction tx = transactions.save(new WalletTransaction(TransactionType.TOPUP, null, walletId,
                amount, wallet.getCurrency(), note, idempotencyKey, requestHash, now));
        LedgerEntry credit = ledger.save(new LedgerEntry(tx.getId(), walletId, EntryType.CREDIT, amount,
                wallet.getBalance(), now));

        audit.info("type=TOPUP txId={} to={} amount={} currency={} idempotencyKey={}",
                tx.getId(), walletId, amount, wallet.getCurrency(), idempotencyKey);
        return new OperationResult(tx, List.of(credit), false);
    }

    @Transactional
    public OperationResult transfer(String idempotencyKey, String requestHash, Long fromId, Long toId,
                                    BigDecimal amount, String note) {
        Optional<OperationResult> earlier = findReplay(idempotencyKey, requestHash);
        if (earlier.isPresent()) {
            return earlier.get();
        }

        Map<Long, Wallet> locked = lockWallets(List.of(fromId, toId));
        Wallet from = locked.get(fromId);
        Wallet to = locked.get(toId);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }
        LocalDateTime now = LocalDateTime.now(clock);

        from.debit(amount);
        to.credit(amount);
        WalletTransaction tx = transactions.save(new WalletTransaction(TransactionType.TRANSFER, fromId, toId,
                amount, from.getCurrency(), note, idempotencyKey, requestHash, now));
        LedgerEntry debit = ledger.save(new LedgerEntry(tx.getId(), fromId, EntryType.DEBIT, amount,
                from.getBalance(), now));
        LedgerEntry credit = ledger.save(new LedgerEntry(tx.getId(), toId, EntryType.CREDIT, amount,
                to.getBalance(), now));

        audit.info("type=TRANSFER txId={} from={} to={} amount={} currency={} idempotencyKey={}",
                tx.getId(), fromId, toId, amount, from.getCurrency(), idempotencyKey);
        return new OperationResult(tx, List.of(debit, credit), false);
    }

    /**
     * Looks up an earlier request with the same key. Same key and same request returns the original result;
     * same key with a different request is an error.
     */
    @Transactional(readOnly = true)
    public Optional<OperationResult> findReplay(String idempotencyKey, String requestHash) {
        return transactions.findByIdempotencyKey(idempotencyKey).map(tx -> {
            if (!tx.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return new OperationResult(tx, ledger.findByTransactionIdOrderByIdAsc(tx.getId()), true);
        });
    }

    /** Locks the rows in id order (see WalletRepository) and fails with 404 if any wallet is missing. */
    private Map<Long, Wallet> lockWallets(List<Long> ids) {
        Map<Long, Wallet> found = wallets.lockAllById(ids).stream()
                .collect(Collectors.toMap(Wallet::getId, w -> w));
        for (Long id : ids) {
            if (!found.containsKey(id)) {
                throw new WalletNotFoundException(id);
            }
        }
        return found;
    }
}
