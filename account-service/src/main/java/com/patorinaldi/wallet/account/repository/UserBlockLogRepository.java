package com.patorinaldi.wallet.account.repository;

import com.patorinaldi.wallet.account.entity.UserBlockLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserBlockLogRepository extends JpaRepository<UserBlockLog, UUID> {

    boolean existsByTriggeredByTransactionId(UUID transactionId);
    List<UserBlockLog> findByUserId(UUID userId);
}
