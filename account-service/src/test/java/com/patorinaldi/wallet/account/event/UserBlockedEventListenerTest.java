package com.patorinaldi.wallet.account.event;

import com.patorinaldi.wallet.account.exception.UserNotFoundException;
import com.patorinaldi.wallet.account.service.UserService;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserBlockedEventListenerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserBlockedEventListener listener;

    @Test
    void handleUserBlocked_shouldCallUserService_whenEventIsReceived() {
        // Given
        UserBlockedEvent event = new UserBlockedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Automated fraud detection",
                90,
                Instant.now()
        );

        // When
        listener.handleUserBlocked(event);

        // Then
        verify(userService).blockUser(
                event.userId(),
                event.triggeredByTransactionId(),
                event.reason(),
                event.riskScore(),
                event.blockedAt()
        );

        verify(userService, times(1)).blockUser(any(), any(), any(), any(), any());
    }

    @Test
    void handleUserBlocked_shouldThrowException_whenUserNotFound() {
        // Given
        UserBlockedEvent event = new UserBlockedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Reason",
                100,
                Instant.now()
        );
        doThrow(new UserNotFoundException(event.userId()))
                .when(userService)
                .blockUser(
                        event.userId(),
                        event.triggeredByTransactionId(),
                        event.reason(),
                        event.riskScore(),
                        event.blockedAt()
                );

        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            listener.handleUserBlocked(event);
        });
        verify(userService).blockUser(
                event.userId(),
                event.triggeredByTransactionId(),
                event.reason(),
                event.riskScore(),
                event.blockedAt()
        );
    }

}
