package com.patorinaldi.wallet.notification.event;

import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
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
public class WalletCreatedEventListener {

    private final UserInfoRepository userInfoRepository;
    private final EmailService emailService;
    private final NotificationIdempotencyService idempotencyService;

    @KafkaListener(topics = "wallet-created", groupId = "notification-service")
    public void handleWalletCreated(WalletCreatedEvent event) {
        log.info("Received wallet-created event for walletId: {}", event.walletId());

        if (idempotencyService.isProcessed(event.walletId().toString())) {
            log.info("Wallet-created event {} already processed, skipping", event.walletId());
            return;
        }

        UserInfo user = userInfoRepository.findById(event.userId())
                .orElseThrow(() -> new RetryableException(
                        "UserInfo not found for userId: " + event.userId() + ". Will retry."));

        emailService.sendWalletCreatedEmail(user, event);
        log.info("Sent wallet-created email to user: {}", event.userId());
    }
}
