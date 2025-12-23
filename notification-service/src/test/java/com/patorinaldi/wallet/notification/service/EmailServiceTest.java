package com.patorinaldi.wallet.notification.service;

import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.FraudAlertEvent;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.notification.entity.NotificationLog;
import com.patorinaldi.wallet.notification.entity.NotificationStatus;
import com.patorinaldi.wallet.notification.entity.NotificationType;
import com.patorinaldi.wallet.notification.entity.UserInfo;
import com.patorinaldi.wallet.notification.repository.NotificationLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private MimeMessage mimeMessage;

    private EmailService emailService;

    @BeforeEach
    void setUp() throws Exception {
        emailService = new EmailService(mailSender, templateEngine, notificationLogRepository, meterRegistry);

        setField(emailService, "fromEmail", "noreply@wallet.com");
        setField(emailService, "fromName", "Wallet Notifications");
        setField(emailService, "adminEmail", "admin@wallet.com");

        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void sendWelcomeEmail_shouldSendEmailAndSaveLog() {
        UserInfo user = UserInfo.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .createdAt(Instant.now())
                .build();

        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .recordId(UUID.randomUUID())
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .registeredAt(Instant.now())
                .build();

        when(templateEngine.process(eq("welcome"), any(Context.class))).thenReturn("<html>Welcome</html>");

        emailService.sendWelcomeEmail(user, event);

        verify(mailSender).send(mimeMessage);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getEventId()).isEqualTo(event.recordId());
        assertThat(savedLog.getNotificationType()).isEqualTo(NotificationType.WELCOME);
        assertThat(savedLog.getRecipientEmail()).isEqualTo(user.getEmail());
        assertThat(savedLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(savedLog.getSourceUserId()).isEqualTo(user.getUserId());
    }

    @Test
    void sendTransactionReceipt_shouldSendEmailAndSaveLog() {
        UserInfo user = UserInfo.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .createdAt(Instant.now())
                .build();

        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(user.getUserId())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("500.00"))
                .completedAt(Instant.now())
                .build();

        when(templateEngine.process(eq("transaction-receipt"), any(Context.class)))
                .thenReturn("<html>Receipt</html>");

        emailService.sendTransactionReceipt(user, event);

        verify(mailSender).send(mimeMessage);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getEventId()).isEqualTo(event.transactionId());
        assertThat(savedLog.getNotificationType()).isEqualTo(NotificationType.TRANSACTION);
        assertThat(savedLog.getSourceTransactionId()).isEqualTo(event.transactionId());
    }

    @Test
    void sendFraudAlertToAdmin_shouldSendToAdminEmail() {
        FraudAlertEvent event = FraudAlertEvent.builder()
                .analysisId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .riskScore(85)
                .decision("BLOCK")
                .build();

        when(templateEngine.process(eq("fraud-alert"), any(Context.class)))
                .thenReturn("<html>Fraud Alert</html>");

        emailService.sendFraudAlertToAdmin(event);

        verify(mailSender).send(mimeMessage);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getEventId()).isEqualTo(event.analysisId());
        assertThat(savedLog.getNotificationType()).isEqualTo(NotificationType.FRAUD_ALERT);
        assertThat(savedLog.getRecipientEmail()).isEqualTo("admin@wallet.com");
    }
}
