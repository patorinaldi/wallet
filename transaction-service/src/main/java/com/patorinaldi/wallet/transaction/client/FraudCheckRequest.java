package com.patorinaldi.wallet.transaction.client;

import com.patorinaldi.wallet.common.enums.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record FraudCheckRequest(
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        TransactionType transactionType,
        String currency
) {
}
