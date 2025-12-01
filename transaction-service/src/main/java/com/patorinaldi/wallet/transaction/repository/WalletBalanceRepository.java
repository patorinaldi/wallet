package com.patorinaldi.wallet.transaction.repository;

import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletBalanceRepository extends JpaRepository<WalletBalance, UUID> {

    Optional<WalletBalance> findByWalletId(UUID walletId);

    boolean existsByWalletId(UUID walletId);

    List<WalletBalance> findByUserId(UUID userId);
}
