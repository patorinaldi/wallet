package com.patorinaldi.wallet.transaction.helper;

import com.patorinaldi.wallet.common.enums.TransactionStatus;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
import com.patorinaldi.wallet.transaction.dto.DepositRequest;
import com.patorinaldi.wallet.transaction.dto.TransferRequest;
import com.patorinaldi.wallet.transaction.dto.WithdrawalRequest;
import com.patorinaldi.wallet.transaction.entity.Transaction;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TestDataBuilder {

    public static WalletBalance createWalletBalance(UUID walletId, UUID userId, BigDecimal balance, String currency) {
        return WalletBalance.builder()
                .walletId(walletId)
                .userId(userId)
                .balance(balance)
                .currency(currency)
                .build();
    }

    public static DepositRequest createDepositRequest(UUID walletId, BigDecimal amount, String description) {
        return new DepositRequest(
                walletId,
                amount,
                description,
                "IDEMPOTENCY-" + UUID.randomUUID()
        );
    }

    public static DepositRequest createDepositRequest(UUID walletId, BigDecimal amount, String description, String idempotencyKey) {
        return new DepositRequest(
                walletId,
                amount,
                description,
                idempotencyKey
        );
    }

    public static WithdrawalRequest createWithdrawalRequest(UUID walletId, BigDecimal amount, String description) {
        return new WithdrawalRequest(
                walletId,
                amount,
                description,
                "IDEMPOTENCY-" + UUID.randomUUID()
        );
    }

    public static WithdrawalRequest createWithdrawalRequest(UUID walletId, BigDecimal amount, String description, String idempotencyKey) {
        return new WithdrawalRequest(
                walletId,
                amount,
                description,
                idempotencyKey
        );
    }

    public static TransferRequest createTransferRequest(UUID sourceWalletId, UUID destWalletId, BigDecimal amount, String description) {
        return new TransferRequest(
                sourceWalletId,
                destWalletId,
                amount,
                description,
                "IDEMPOTENCY-" + UUID.randomUUID()
        );
    }

    public static TransferRequest createTransferRequest(UUID sourceWalletId, UUID destWalletId, BigDecimal amount, String description, String idempotencyKey) {
        return new TransferRequest(
                sourceWalletId,
                destWalletId,
                amount,
                description,
                idempotencyKey
        );
    }

    public static WalletCreatedEvent createWalletCreatedEvent(UUID walletId, UUID userId, String currency) {
        return new WalletCreatedEvent(
                UUID.randomUUID(),
                walletId,
                userId,
                currency,
                Instant.now()
        );
    }

    public static Transaction createTransaction(TransactionType type, TransactionStatus status,
                                                UUID walletId, UUID userId, BigDecimal amount,
                                                String currency, String idempotencyKey) {
        return Transaction.builder()
                .type(type)
                .status(status)
                .walletId(walletId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    public static Transaction createTransaction(UUID walletId, UUID userId, TransactionType type, BigDecimal amount) {
        return Transaction.builder()
                .type(type)
                .status(TransactionStatus.COMPLETED)
                .walletId(walletId)
                .userId(userId)
                .amount(amount)
                .currency("USD")
                .idempotencyKey("IDEMPOTENCY-" + UUID.randomUUID())
                .build();
    }
}
