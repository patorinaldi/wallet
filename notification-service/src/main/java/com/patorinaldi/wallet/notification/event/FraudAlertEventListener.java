package com.patorinaldi.wallet.notification.event;

import com.patorinaldi.wallet.common.event.FraudAlertEvent;
import com.patorinaldi.wallet.notification.service.EmailService;
import com.patorinaldi.wallet.notification.service.NotificationIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FraudAlertEventListener {

    private final EmailService emailService;
    private final NotificationIdempotencyService idempotencyService;

    @KafkaListener(topics = "fraud-alert", groupId = "notification-service")
    public void handleFraudAlert(FraudAlertEvent event) {
        log.info("Received fraud-alert event for analysisId: {}", event.analysisId());

        if (idempotencyService.isProcessed(event.analysisId().toString())) {
            log.info("Fraud-alert event {} already processed, skipping", event.analysisId());
            return;
        }

        emailService.sendFraudAlertToAdmin(event);
        log.info("Sent fraud alert to admin for analysisId: {}", event.analysisId());
    }
}
