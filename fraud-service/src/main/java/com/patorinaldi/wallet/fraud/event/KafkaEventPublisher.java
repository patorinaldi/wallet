package com.patorinaldi.wallet.fraud.event;

import com.patorinaldi.wallet.common.event.FraudAlertEvent;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFraudAlert(FraudAlertEvent event) {
        log.debug("Publishing FraudAlertEvent for analysisId: {}", event.analysisId());
        kafkaTemplate.send("fraud-alert", event.analysisId().toString(), event);
        log.info("Published FraudAlertEvent for analysisId: {}", event.analysisId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserBlocked(UserBlockedEvent event) {
        log.debug("Publishing UserBlockedEvent for userId: {}", event.userId());
        kafkaTemplate.send("user-blocked", event.userId().toString(), event);
        log.info("Published UserBlockedEvent for userId: {}", event.userId());
    }
}