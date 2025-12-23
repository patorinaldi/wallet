package com.patorinaldi.wallet.notification.service;

import com.patorinaldi.wallet.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationIdempotencyServiceTest {

    @Mock
    private NotificationLogRepository repository;

    private NotificationIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new NotificationIdempotencyService(repository);
    }

    @Test
    void isProcessed_shouldReturnTrue_whenEventExists() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(true);

        boolean result = service.isProcessed(eventId.toString());

        assertThat(result).isTrue();
    }

    @Test
    void isProcessed_shouldReturnFalse_whenEventDoesNotExist() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);

        boolean result = service.isProcessed(eventId.toString());

        assertThat(result).isFalse();
    }
}
