package com.patorinaldi.wallet.notification.event;

import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
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
public class TransactionCompletedEventListener {

    private final UserInfoRepository userInfoRepository;
    private final EmailService emailService;
    private final NotificationIdempotencyService idempotencyService;

    @KafkaListener(topics = "transaction-completed", groupId = "notification-service")
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Received transaction-completed event for transactionId: {}", event.transactionId());

        if (idempotencyService.isProcessed(event.transactionId().toString())) {
            log.info("Transaction-completed event {} already processed, skipping", event.transactionId());
            return;
        }

        UserInfo user = userInfoRepository.findById(event.userId())
                .orElseThrow(() -> new RetryableException(
                        "UserInfo not found for userId: " + event.userId() + ". Will retry."));

        emailService.sendTransactionReceipt(user, event);
        log.info("Sent transaction receipt to user: {}", event.userId());
    }
}
