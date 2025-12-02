package com.patorinaldi.wallet.transaction.event;

import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.common.event.TransactionFailedEvent;
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
    public void handleTransactionCompletedEvent (TransactionCompletedEvent event) {
        log.debug("Transaction completed Id: {}", event.transactionId());
        kafkaTemplate.send("transaction-completed", event.transactionId().toString(), event);
        log.info("Published TransactionCompletedEvent for Id: {}", event.transactionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionFailedEvent  (TransactionFailedEvent event) {
        log.debug("Transaction failed Id: {}", event.transactionId());
        kafkaTemplate.send("transaction-failed", event.transactionId().toString(), event);
        log.info("Published TransactionFailedEvent  for Id: {}", event.transactionId());
    }
}
