package com.patorinaldi.wallet.notification.event;

import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.notification.entity.UserInfo;
import com.patorinaldi.wallet.notification.repository.UserInfoRepository;
import com.patorinaldi.wallet.notification.service.EmailService;
import com.patorinaldi.wallet.notification.service.NotificationIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private final UserInfoRepository userInfoRepository;
    private final EmailService emailService;
    private final NotificationIdempotencyService idempotencyService;

    @KafkaListener(topics = "user-registered", groupId = "notification-service")
    @Transactional
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received user-registered event for userId: {}", event.userId());

        if (idempotencyService.isProcessed(event.recordId().toString())) {
            log.info("User-registered event {} already processed, skipping", event.recordId());
            return;
        }

        UserInfo user = userInfoRepository.findById(event.userId())
                .orElseGet(() -> userInfoRepository.save(UserInfo.builder()
                        .userId(event.userId())
                        .email(event.email())
                        .fullName(event.fullName())
                        .createdAt(Instant.now())
                        .build()));

        emailService.sendWelcomeEmail(user, event);
        log.info("Sent welcome email to user: {}", event.userId());
    }
}
