package com.patorinaldi.wallet.notification.event;

import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.notification.entity.UserInfo;
import com.patorinaldi.wallet.notification.exception.RetryableException;
import com.patorinaldi.wallet.notification.repository.UserInfoRepository;
import com.patorinaldi.wallet.notification.service.EmailService;
import com.patorinaldi.wallet.notification.service.NotificationIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionCompletedEventListenerTest {

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationIdempotencyService idempotencyService;

    private TransactionCompletedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TransactionCompletedEventListener(userInfoRepository, emailService, idempotencyService);
    }

    @Test
    void handleTransactionCompleted_shouldSendReceipt() {
        UUID userId = UUID.randomUUID();
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("500.00"))
                .completedAt(Instant.now())
                .build();

        UserInfo user = UserInfo.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("Test User")
                .createdAt(Instant.now())
                .build();

        when(idempotencyService.isProcessed(event.transactionId().toString())).thenReturn(false);
        when(userInfoRepository.findById(userId)).thenReturn(Optional.of(user));

        listener.handleTransactionCompleted(event);

        verify(emailService).sendTransactionReceipt(user, event);
    }

    @Test
    void handleTransactionCompleted_shouldSkipIfAlreadyProcessed() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("500.00"))
                .completedAt(Instant.now())
                .build();

        when(idempotencyService.isProcessed(event.transactionId().toString())).thenReturn(true);

        listener.handleTransactionCompleted(event);

        verifyNoInteractions(userInfoRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void handleTransactionCompleted_shouldThrowRetryableException_whenUserNotFound() {
        UUID userId = UUID.randomUUID();
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("500.00"))
                .completedAt(Instant.now())
                .build();

        when(idempotencyService.isProcessed(event.transactionId().toString())).thenReturn(false);
        when(userInfoRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener.handleTransactionCompleted(event))
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("UserInfo not found");

        verifyNoInteractions(emailService);
    }
}
