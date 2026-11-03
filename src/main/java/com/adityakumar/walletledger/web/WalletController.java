package com.adityakumar.walletledger.web;

import com.adityakumar.walletledger.dto.Dtos.CreateWalletRequest;
import com.adityakumar.walletledger.dto.Dtos.LedgerEntryResponse;
import com.adityakumar.walletledger.dto.Dtos.PageResponse;
import com.adityakumar.walletledger.dto.Dtos.ReconciliationResponse;
import com.adityakumar.walletledger.dto.Dtos.TopUpRequest;
import com.adityakumar.walletledger.dto.Dtos.TransactionResponse;
import com.adityakumar.walletledger.dto.Dtos.WalletResponse;
import com.adityakumar.walletledger.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        WalletResponse created = service.createWallet(request);
        return ResponseEntity.created(URI.create("/api/wallets/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable Long id) {
        return service.getWallet(id);
    }

    @PostMapping("/{id}/topups")
    public ResponseEntity<TransactionResponse> topUp(@PathVariable Long id,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @Valid @RequestBody TopUpRequest request) {
        return TransactionResponses.of(service.topUp(idempotencyKey, id, request));
    }

    /** Newest entries first. Example: ?page=0&size=20 */
    @GetMapping("/{id}/ledger")
    public PageResponse<LedgerEntryResponse> ledger(@PathVariable Long id,
                                                    @PageableDefault(size = 20) Pageable pageable) {
        return service.ledger(id, pageable);
    }

    @GetMapping("/{id}/reconciliation")
    public ReconciliationResponse reconciliation(@PathVariable Long id) {
        return service.reconcile(id);
    }
}
