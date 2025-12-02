package com.patorinaldi.wallet.transaction.service;

import com.patorinaldi.wallet.common.enums.TransactionStatus;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.transaction.entity.Transaction;
import com.patorinaldi.wallet.transaction.helper.TestDataBuilder;
import com.patorinaldi.wallet.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionPersistenceServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionPersistenceService transactionPersistenceService;

    @Test
    void saveFailedTransaction_shouldSaveInNewTransaction() {
        // Given
        Transaction failedTransaction = TestDataBuilder.createTransaction(
                TransactionType.WITHDRAWAL,
                TransactionStatus.FAILED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "USD",
                "TEST-KEY"
        );
        failedTransaction.fail("Insufficient balance");

        when(transactionRepository.save(any(Transaction.class))).thenReturn(failedTransaction);

        // When
        transactionPersistenceService.saveFailedTransaction(failedTransaction);

        // Then
        verify(transactionRepository).save(failedTransaction);
    }
}
