package com.patorinaldi.wallet.transaction.repository;

import com.patorinaldi.wallet.transaction.entity.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, UUID> {
    boolean existsByUserId(UUID userId);
    boolean existsByTriggeredByTransactionId(UUID transactionId);
}