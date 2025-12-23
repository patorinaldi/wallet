package com.patorinaldi.wallet.notification.repository;

import com.patorinaldi.wallet.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    boolean existsByEventId(UUID eventId);

    Optional<NotificationLog> findByEventId(UUID eventId);
}
