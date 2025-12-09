package com.patorinaldi.wallet.transaction.service;

import com.patorinaldi.wallet.common.enums.TransactionStatus;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.common.event.TransactionFailedEvent;
import com.patorinaldi.wallet.transaction.dto.*;
import com.patorinaldi.wallet.transaction.entity.Transaction;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.exception.*;
import com.patorinaldi.wallet.transaction.mapper.BalanceMapper;
import com.patorinaldi.wallet.transaction.mapper.TransactionMapper;
import com.patorinaldi.wallet.transaction.repository.BlockedUserRepository;
import com.patorinaldi.wallet.transaction.repository.TransactionRepository;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final TransactionMapper transactionMapper;
    private final TransactionPersistenceService persistenceService;
    private final BalanceMapper balanceMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final BlockedUserRepository blockedUserRepository;

    @Transactional
    public TransactionResponse deposit(DepositRequest request) {

        log.info("Processing deposit for wallet: {}, amount: {}",
                request.walletId(), request.amount());

        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            log.warn("Duplicate transaction detected: {}", request.idempotencyKey());
            throw new DuplicateTransactionException(request.idempotencyKey());
        }

        WalletBalance walletBalance = walletBalanceRepository.findByWalletId(request.walletId())
                .orElseThrow(() -> {
                    log.error("Wallet balance not found: {}", request.walletId());
                    return new WalletBalanceNotFoundException(request.walletId());
                });

        validateUserNotBlocked(walletBalance.getUserId());

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

        TransactionCompletedEvent event = buildCompletedEvent(transaction);
        eventPublisher.publishEvent(event);

        log.info("Deposit completed successfully for wallet: {}, new balance: {}",
                walletBalance.getWalletId(), walletBalance.getBalance());

        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse withdrawal(WithdrawalRequest request) {

        log.info("Processing withdrawal for wallet: {}, amount: {}",
                request.walletId(), request.amount());

        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            log.warn("Duplicate transaction detected: {}", request.idempotencyKey());
            throw new DuplicateTransactionException(request.idempotencyKey());
        }

        WalletBalance walletBalance = walletBalanceRepository.findByWalletId(request.walletId())
                .orElseThrow(() -> {
                    log.error("Wallet balance not found: {}", request.walletId());
                    return new WalletBalanceNotFoundException(request.walletId());
                });

        validateUserNotBlocked(walletBalance.getUserId());

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

        try {
            walletBalance.debit(transaction.getAmount());
            transaction.complete(walletBalance.getBalance());
            transactionRepository.save(transaction);
            walletBalanceRepository.save(walletBalance);
            log.info("Withdrawal completed successfully for wallet: {}, new balance: {}",
                    walletBalance.getWalletId(), walletBalance.getBalance());
            TransactionCompletedEvent event = buildCompletedEvent(transaction);
            eventPublisher.publishEvent(event);
        } catch (InsufficientBalanceException e) {
            log.error("Insufficient balance for withdrawal...");
            transaction.fail(e.getMessage());
            persistenceService.saveFailedTransaction(transaction);
            TransactionFailedEvent event = buildFailedEvent(transaction);
            eventPublisher.publishEvent(event);
            throw e;
        }

        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request) {

        log.info("Processing transfer from wallet: {} to wallet: {}, amount: {}",
                request.sourceWalletId(), request.destinationWalletId(), request.amount());

        String outKey = request.idempotencyKey() + ":out";
        String inKey = request.idempotencyKey() + ":in";

        if (transactionRepository.existsByIdempotencyKey(outKey) || transactionRepository.existsByIdempotencyKey(inKey)) {
            log.warn("Duplicate transaction detected: {}", request.idempotencyKey());
            throw new DuplicateTransactionException(request.idempotencyKey());
        }

        WalletBalance sourceWalletBalance = walletBalanceRepository.findByWalletId(request.sourceWalletId())
                .orElseThrow(() -> {
                    log.error("Source wallet balance not found: {}", request.sourceWalletId());
                    return new WalletBalanceNotFoundException(request.sourceWalletId());
                });

        WalletBalance destinationWalletBalance = walletBalanceRepository.findByWalletId(request.destinationWalletId())
                .orElseThrow(() -> {
                    log.error("Destination wallet balance not found: {}", request.destinationWalletId());
                    return new WalletBalanceNotFoundException(request.destinationWalletId());
                });

        validateUserNotBlocked(sourceWalletBalance.getUserId());
        validateUserNotBlocked(destinationWalletBalance.getUserId());

        Transaction transactionOut = Transaction.builder()
                .amount(request.amount())
                .idempotencyKey(outKey)
                .type(TransactionType.TRANSFER_OUT)
                .status(TransactionStatus.PENDING)
                .walletId(sourceWalletBalance.getWalletId())
                .userId(sourceWalletBalance.getUserId())
                .currency(sourceWalletBalance.getCurrency())
                .description(request.description())
                .build();

        Transaction transactionIn = Transaction.builder()
                .amount(request.amount())
                .idempotencyKey(inKey)
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.PENDING)
                .walletId(destinationWalletBalance.getWalletId())
                .userId(destinationWalletBalance.getUserId())
                .currency(destinationWalletBalance.getCurrency())
                .description(request.description())
                .build();

        try {
            sourceWalletBalance.debit(transactionOut.getAmount());
            destinationWalletBalance.credit(transactionIn.getAmount());
            transactionOut.complete(sourceWalletBalance.getBalance());
            transactionIn.complete(destinationWalletBalance.getBalance());

            transactionRepository.save(transactionOut);
            transactionRepository.save(transactionIn);

            transactionOut.setRelatedWalletId(transactionIn.getWalletId());
            transactionOut.setRelatedTransactionId(transactionIn.getId());

            transactionIn.setRelatedWalletId(transactionOut.getWalletId());
            transactionIn.setRelatedTransactionId(transactionOut.getId());

            transactionRepository.save(transactionOut);
            transactionRepository.save(transactionIn);

            walletBalanceRepository.save(sourceWalletBalance);
            walletBalanceRepository.save(destinationWalletBalance);
            log.info("Transfer completed successfully for source wallet: {}, new balance: {}, destination wallet: {}, new balance: {}",
                    sourceWalletBalance.getWalletId(), sourceWalletBalance.getBalance(),
                    destinationWalletBalance.getWalletId(), destinationWalletBalance.getBalance());

            TransactionCompletedEvent eventOut = buildCompletedEvent(transactionOut);
            eventPublisher.publishEvent(eventOut);
            TransactionCompletedEvent eventIn = buildCompletedEvent(transactionIn);
            eventPublisher.publishEvent(eventIn);

        } catch (InsufficientBalanceException e) {
            log.error("Insufficient balance for transfer...");
            transactionOut.fail(e.getMessage());
            persistenceService.saveFailedTransaction(transactionOut);
            TransactionFailedEvent event = buildFailedEvent(transactionOut);
            eventPublisher.publishEvent(event);
            throw e;
        }

        return transactionMapper.toResponse(transactionOut);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByWallet(UUID walletId, Pageable pageable) {
        final Page<Transaction> transactions = transactionRepository.findByWalletId(walletId, pageable);

        return transactions.map(transactionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID id) {
        return transactionMapper.toResponse(
                transactionRepository.findById(id).orElseThrow(() -> {
                    log.warn("Transaction not found for id: {}", id);
                    return new TransactionNotFoundException(id);
                })
        );
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID walletId) {
        return balanceMapper.toResponse(
                walletBalanceRepository.findByWalletId(walletId).orElseThrow(
                        () -> {
                            log.warn("Wallet balance not found: {}", (walletId));
                            return new WalletBalanceNotFoundException(walletId);
                        }
                )
        );
    }

    private TransactionCompletedEvent buildCompletedEvent(Transaction transaction) {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(transaction.getId())
                .type(transaction.getType())
                .walletId(transaction.getWalletId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .balanceAfter(transaction.getBalanceAfter())
                .relatedWalletId(transaction.getRelatedWalletId())
                .relatedTransactionId(transaction.getRelatedTransactionId())
                .description(transaction.getDescription())
                .completedAt(Instant.now())
                .build();
    }

    private TransactionFailedEvent buildFailedEvent(Transaction transaction) {
        return TransactionFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(transaction.getId())
                .type(transaction.getType())
                .walletId(transaction.getWalletId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .relatedTransactionId(transaction.getRelatedTransactionId())
                .description(transaction.getDescription())
                .failedAt(Instant.now())
                .errorReason(transaction.getErrorMessage())
                .build();
    }

    private void validateUserNotBlocked(UUID userId) {
        blockedUserRepository.findById(userId).ifPresent(blocked -> {
            log.warn("Transaction rejected - user {} is blocked: {}", userId, blocked.getReason());
            throw new UserBlockedException(userId, blocked.getReason());
        });
    }

}