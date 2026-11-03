package com.adityakumar.walletledger.service;

import com.adityakumar.walletledger.domain.LedgerEntry;
import com.adityakumar.walletledger.domain.WalletTransaction;

import java.util.List;

/** The outcome of a top-up or transfer. {@code replayed} is true when an earlier identical request is returned. */
public record OperationResult(WalletTransaction transaction, List<LedgerEntry> entries, boolean replayed) {
}
