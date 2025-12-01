package com.patorinaldi.wallet.transaction.exception;

import lombok.Getter;
import java.util.UUID;

@Getter
public class WalletBalanceNotFoundException extends RuntimeException {

    private final UUID walletId;

    public WalletBalanceNotFoundException(UUID walletId) {
        super(String.format("Wallet balance not found for wallet: %s", walletId));
        this.walletId = walletId;
    }
}