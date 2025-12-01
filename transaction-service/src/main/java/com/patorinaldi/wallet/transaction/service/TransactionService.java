package com.patorinaldi.wallet.transaction.service;

import com.patorinaldi.wallet.common.enums.TransactionStatus;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.transaction.dto.DepositRequest;
import com.patorinaldi.wallet.transaction.dto.TransactionResponse;
import com.patorinaldi.wallet.transaction.dto.WithdrawalRequest;
import com.patorinaldi.wallet.transaction.entity.Transaction;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.exception.DuplicateTransactionException;
import com.patorinaldi.wallet.transaction.exception.InsufficientBalanceException;
import com.patorinaldi.wallet.transaction.exception.WalletBalanceNotFoundException;
import com.patorinaldi.wallet.transaction.mapper.TransactionMapper;
import com.patorinaldi.wallet.transaction.repository.TransactionRepository;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final TransactionMapper transactionMapper;
    private final TransactionPersistenceService persistenceService;

    @Transactional
    public TransactionResponse deposit(DepositRequest request) {

        log.info("Processing deposit for wallet: {}, amount: {}",
                request.walletId(), request.amount());

        if(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            log.warn("Duplicate transaction detected: {}", request.idempotencyKey());
            throw new DuplicateTransactionException(request.idempotencyKey());
        }

        WalletBalance walletBalance = walletBalanceRepository.findByWalletId(request.walletId())
                .orElseThrow(() -> {
                            log.error("Wallet balance not found: {}", request.walletId());
                            return new WalletBalanceNotFoundException(request.walletId());
                        });

        Transaction transaction = Transaction.builder()
                .amount(request.amount())
                .idempotencyKey(request.idempotencyKey())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .walletId(walletBalance.getWalletId())
                .userId(walletBalance.getUserId())
                .currency(walletBalance.getCurrency())
                .description(request.description())
                .build();

        walletBalance.credit(transaction.getAmount());
        transaction.complete(walletBalance.getBalance());

        transactionRepository.save(transaction);
        walletBalanceRepository.save(walletBalance);

        log.info("Deposit completed successfully for wallet: {}, new balance: {}",
                walletBalance.getWalletId(), walletBalance.getBalance());

        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse withdrawal(WithdrawalRequest request) {

        log.info("Processing withdrawal for wallet: {}, amount: {}",
                request.walletId(), request.amount());

        if(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            log.warn("Duplicate transaction detected: {}", request.idempotencyKey());
            throw new DuplicateTransactionException(request.idempotencyKey());
        }

        WalletBalance walletBalance = walletBalanceRepository.findByWalletId(request.walletId())
                .orElseThrow(() -> {
                    log.error("Wallet balance not found: {}", request.walletId());
                    return new WalletBalanceNotFoundException(request.walletId());
                });

        Transaction transaction = Transaction.builder()
                .amount(request.amount())
                .idempotencyKey(request.idempotencyKey())
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PENDING)
                .walletId(walletBalance.getWalletId())
                .userId(walletBalance.getUserId())
                .currency(walletBalance.getCurrency())
                .description(request.description())
                .build();

        try  {
            walletBalance.debit(transaction.getAmount());
            transaction.complete(walletBalance.getBalance());
            transactionRepository.save(transaction);
            walletBalanceRepository.save(walletBalance);
            log.info("Withdrawal completed successfully for wallet: {}, new balance: {}",
                    walletBalance.getWalletId(), walletBalance.getBalance());
        } catch (InsufficientBalanceException e) {
            log.error("Insufficient balance for withdrawal...");
            transaction.fail(e.getMessage());
            persistenceService.saveFailedTransaction(transaction);
            throw e;
        }

        return transactionMapper.toResponse(transaction);
    }
}