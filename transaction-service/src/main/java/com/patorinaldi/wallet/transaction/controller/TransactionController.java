package com.patorinaldi.wallet.transaction.controller;

import com.patorinaldi.wallet.transaction.dto.*;
import com.patorinaldi.wallet.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        log.info("Deposit request received for wallet: {}, amount: {}",
                request.walletId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.deposit(request));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<TransactionResponse> withdrawal(@Valid @RequestBody WithdrawalRequest request) {
        log.info("Withdrawal request received for wallet: {}, amount: {}",
                request.walletId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.withdrawal(request));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        log.info("Transfer request received source wallet: {}, destination wallet: {}, amount: {}",
                request.sourceWalletId(), request.destinationWalletId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.transfer(request));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @RequestParam UUID walletId,
            Pageable pageable
    ) {
        return ResponseEntity.ok().body(
                transactionService.getTransactionsByWallet(walletId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {

        return ResponseEntity.ok().body(
                transactionService.getTransactionById(id));
    }

    @GetMapping("/balances/{walletId}")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID walletId) {
        return ResponseEntity.ok(
                transactionService.getBalance(walletId)
        );
    }
}