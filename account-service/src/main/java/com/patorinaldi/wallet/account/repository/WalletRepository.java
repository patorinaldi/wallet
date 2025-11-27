package com.patorinaldi.wallet.account.repository;

import com.patorinaldi.wallet.account.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findByUserId(UUID userId);

    List<Wallet> findByUserIdAndActiveTrue(UUID userId);

    boolean existsByUserIdAndCurrency(UUID userId, String currency);

}
