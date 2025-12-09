package com.patorinaldi.wallet.fraud.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.fraud.service.FraudAnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final FraudAnalysisService fraudAnalysisService;

    @KafkaListener(topics = "transaction-completed", groupId = "fraud-service")
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Received TransactionCompletedEvent: {}", event); 
        fraudAnalysisService.analyzeTransaction(event);
        log.info("Completed fraud analysis for transaction: {}", event.transactionId());
    }
    
}
