package com.patorinaldi.wallet.notification.event;

import com.patorinaldi.wallet.common.event.TransactionFailedEvent;
import com.patorinaldi.wallet.notification.entity.UserInfo;
import com.patorinaldi.wallet.notification.exception.RetryableException;
import com.patorinaldi.wallet.notification.repository.UserInfoRepository;
import com.patorinaldi.wallet.notification.service.EmailService;
import com.patorinaldi.wallet.notification.service.NotificationIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionFailedEventListener {

    private final UserInfoRepository userInfoRepository;
    private final EmailService emailService;
    private final NotificationIdempotencyService idempotencyService;

    @KafkaListener(topics = "transaction-failed", groupId = "notification-service")
    public void handleTransactionFailed(TransactionFailedEvent event) {
        log.info("Received transaction-failed event for transactionId: {}", event.transactionId());

        if (idempotencyService.isProcessed(event.transactionId().toString())) {
            log.info("Transaction-failed event {} already processed, skipping", event.transactionId());
            return;
        }

        UserInfo user = userInfoRepository.findById(event.userId())
                .orElseThrow(() -> new RetryableException(
                        "UserInfo not found for userId: " + event.userId() + ". Will retry."));

        emailService.sendTransactionFailedNotification(user, event);
        log.info("Sent transaction-failed notification to user: {}", event.userId());
    }
}
