package com.patorinaldi.wallet.notification.event;

import com.patorinaldi.wallet.common.event.UserBlockedEvent;
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
public class UserBlockedEventListener {

    private final UserInfoRepository userInfoRepository;
    private final EmailService emailService;
    private final NotificationIdempotencyService idempotencyService;

    @KafkaListener(topics = "user-blocked", groupId = "notification-service")
    public void handleUserBlocked(UserBlockedEvent event) {
        log.info("Received user-blocked event for userId: {}", event.userId());

        if (idempotencyService.isProcessed(event.triggeredByTransactionId().toString())) {
            log.info("User-blocked event {} already processed, skipping", event.triggeredByTransactionId());
            return;
        }

        userInfoRepository.findById(event.userId())
                .ifPresentOrElse(
                        user -> {
                            emailService.sendAccountBlockedNotification(user, event);
                            log.info("Sent account-blocked notification to user: {}", event.userId());
                        },
                        () -> log.warn("Cannot notify blocked user {} - no cached UserInfo", event.userId())
                );
    }
}
