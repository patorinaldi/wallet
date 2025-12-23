package com.patorinaldi.wallet.notification.service;

import com.patorinaldi.wallet.common.idempotency.IdempotencyChecker;
import com.patorinaldi.wallet.common.idempotency.ProcessingOutcome;
import com.patorinaldi.wallet.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationIdempotencyService implements IdempotencyChecker {

    private final NotificationLogRepository repository;

    @Override
    public boolean isProcessed(String eventId) {
        return repository.existsByEventId(UUID.fromString(eventId));
    }

    @Override
    public void markProcessed(String eventId) {
        // Not used - EmailService saves NotificationLog directly with full context
    }

    @Override
    public void markProcessed(String eventId, ProcessingOutcome outcome) {
        // Not used - EmailService saves NotificationLog directly with full context
    }
}
