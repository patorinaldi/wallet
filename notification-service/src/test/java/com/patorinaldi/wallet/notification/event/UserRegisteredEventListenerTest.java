package com.patorinaldi.wallet.notification.event;

import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.notification.entity.UserInfo;
import com.patorinaldi.wallet.notification.repository.UserInfoRepository;
import com.patorinaldi.wallet.notification.service.EmailService;
import com.patorinaldi.wallet.notification.service.NotificationIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegisteredEventListenerTest {

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationIdempotencyService idempotencyService;

    private UserRegisteredEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new UserRegisteredEventListener(userInfoRepository, emailService, idempotencyService);
    }

    @Test
    void handleUserRegistered_shouldCacheUserAndSendWelcomeEmail() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .recordId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .registeredAt(Instant.now())
                .build();

        when(idempotencyService.isProcessed(event.recordId().toString())).thenReturn(false);
        when(userInfoRepository.findById(event.userId())).thenReturn(Optional.empty());
        when(userInfoRepository.save(any(UserInfo.class))).thenAnswer(i -> i.getArgument(0));

        listener.handleUserRegistered(event);

        ArgumentCaptor<UserInfo> userCaptor = ArgumentCaptor.forClass(UserInfo.class);
        verify(userInfoRepository).save(userCaptor.capture());

        UserInfo savedUser = userCaptor.getValue();
        assertThat(savedUser.getUserId()).isEqualTo(event.userId());
        assertThat(savedUser.getEmail()).isEqualTo(event.email());
        assertThat(savedUser.getFullName()).isEqualTo(event.fullName());

        verify(emailService).sendWelcomeEmail(any(UserInfo.class), eq(event));
    }

    @Test
    void handleUserRegistered_shouldSkipIfAlreadyProcessed() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .recordId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .registeredAt(Instant.now())
                .build();

        when(idempotencyService.isProcessed(event.recordId().toString())).thenReturn(true);

        listener.handleUserRegistered(event);

        verifyNoInteractions(userInfoRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void handleUserRegistered_shouldUseExistingUserIfAlreadyCached() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .recordId(UUID.randomUUID())
                .userId(userId)
                .email("test@example.com")
                .fullName("Test User")
                .registeredAt(Instant.now())
                .build();

        UserInfo existingUser = UserInfo.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("Test User")
                .createdAt(Instant.now())
                .build();

        when(idempotencyService.isProcessed(event.recordId().toString())).thenReturn(false);
        when(userInfoRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        listener.handleUserRegistered(event);

        verify(userInfoRepository, never()).save(any());
        verify(emailService).sendWelcomeEmail(existingUser, event);
    }
}
