package com.adityakumar.walletledger.web;

import com.adityakumar.walletledger.dto.Dtos.TransactionResponse;
import com.adityakumar.walletledger.service.OperationResult;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/** Builds the HTTP answer for a top-up or transfer: 201 for new work, 200 plus a header for a replay. */
final class TransactionResponses {

    static final String REPLAY_HEADER = "Idempotent-Replayed";

    private TransactionResponses() {
    }

    static ResponseEntity<TransactionResponse> of(OperationResult result) {
        TransactionResponse body = TransactionResponse.from(result.transaction(), result.entries());
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAY_HEADER, "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/transactions/" + body.id())).body(body);
    }
}
