package com.patorinaldi.wallet.ledger.event;

import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final LedgerService ledgerService;

    @KafkaListener(topics = "transaction-completed", groupId = "ledger-service")
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Received TransactionCompletedEvent: {}", event);
        ledgerService.processTransaction(event);
        log.info("Processed transaction: {}", event.transactionId());
    }
}
