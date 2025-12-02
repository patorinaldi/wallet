package com.patorinaldi.wallet.transaction.repository;

import com.patorinaldi.wallet.transaction.entity.Transaction;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findByWalletId(UUID walletId, Pageable pageable);

    @Query("SELECT t from Transaction t WHERE t.walletId = :walletId OR t.relatedWalletId = :walletId")
    Page<Transaction> findByWalletIdOrRelatedWalletId(@Param("walletId") UUID walletId, Pageable pageable);

}
