package com.adityakumar.walletledger.web;

import com.adityakumar.walletledger.dto.Dtos.TransactionResponse;
import com.adityakumar.walletledger.dto.Dtos.TransferRequest;
import com.adityakumar.walletledger.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final WalletService service;

    public TransferController(WalletService service) {
        this.service = service;
    }

    @PostMapping("/api/transfers")
    public ResponseEntity<TransactionResponse> transfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        @Valid @RequestBody TransferRequest request) {
        return TransactionResponses.of(service.transfer(idempotencyKey, request));
    }

    @GetMapping("/api/transactions/{id}")
    public TransactionResponse transaction(@PathVariable Long id) {
        return service.getTransaction(id);
    }
}
