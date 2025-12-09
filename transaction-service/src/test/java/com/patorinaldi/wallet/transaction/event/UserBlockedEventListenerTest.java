package com.patorinaldi.wallet.transaction.event;

import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import com.patorinaldi.wallet.transaction.entity.BlockedUser;
import com.patorinaldi.wallet.transaction.repository.BlockedUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBlockedEventListenerTest {

    @Mock
    private BlockedUserRepository blockedUserRepository;

    @InjectMocks
    private UserBlockedEventListener listener;

    @Test
    void handleUserBlocked_shouldSaveBlockedUser_whenEventIsNew() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        String reason = "Fraud detected";
        Integer riskScore = 85;
        Instant blockedAt = Instant.now();

        UserBlockedEvent event = new UserBlockedEvent(
                userId,
                transactionId,
                reason,
                riskScore,
                blockedAt
        );

        when(blockedUserRepository.existsByTriggeredByTransactionId(transactionId))
                .thenReturn(false);

        // When
        listener.handleUserBlocked(event);

        // Then
        verify(blockedUserRepository).existsByTriggeredByTransactionId(transactionId);
        verify(blockedUserRepository).save(argThat(blocked ->
                blocked.getUserId().equals(userId) &&
                blocked.getTriggeredByTransactionId().equals(transactionId) &&
                blocked.getReason().equals(reason) &&
                blocked.getRiskScore().equals(riskScore) &&
                blocked.getBlockedAt().equals(blockedAt)
        ));
    }

    @Test
    void handleUserBlocked_shouldSkip_whenEventAlreadyProcessed() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        UserBlockedEvent event = new UserBlockedEvent(
                userId,
                transactionId,
                "Fraud detected",
                85,
                Instant.now()
        );

        when(blockedUserRepository.existsByTriggeredByTransactionId(transactionId))
                .thenReturn(true);

        // When
        listener.handleUserBlocked(event);

        // Then
        verify(blockedUserRepository).existsByTriggeredByTransactionId(transactionId);
        verify(blockedUserRepository, never()).save(any(BlockedUser.class));
    }
}
