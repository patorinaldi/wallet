package com.patorinaldi.wallet.notification.service;

import com.patorinaldi.wallet.common.event.FraudAlertEvent;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.common.event.TransactionFailedEvent;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
import com.patorinaldi.wallet.notification.entity.NotificationLog;
import com.patorinaldi.wallet.notification.entity.NotificationStatus;
import com.patorinaldi.wallet.notification.entity.NotificationType;
import com.patorinaldi.wallet.notification.entity.UserInfo;
import com.patorinaldi.wallet.notification.repository.NotificationLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationLogRepository notificationLogRepository;
    private final MeterRegistry meterRegistry;

    @Value("${notification.from-email}")
    private String fromEmail;

    @Value("${notification.from-name}")
    private String fromName;

    @Value("${notification.admin-email}")
    private String adminEmail;

    public void sendWelcomeEmail(UserInfo user, UserRegisteredEvent event) {
        String subject = "Welcome to Wallet!";
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("event", event);

        sendAndLog(
                event.recordId(),
                NotificationType.WELCOME,
                user.getEmail(),
                subject,
                "welcome",
                context,
                user.getUserId(),
                null
        );
    }

    public void sendWalletCreatedEmail(UserInfo user, WalletCreatedEvent event) {
        String subject = "New Wallet Created - " + event.currency();
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("event", event);

        sendAndLog(
                event.walletId(),
                NotificationType.WALLET_CREATED,
                user.getEmail(),
                subject,
                "wallet-created",
                context,
                user.getUserId(),
                null
        );
    }

    public void sendTransactionReceipt(UserInfo user, TransactionCompletedEvent event) {
        String subject = "Transaction Receipt - " + event.type();
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("event", event);

        sendAndLog(
                event.transactionId(),
                NotificationType.TRANSACTION,
                user.getEmail(),
                subject,
                "transaction-receipt",
                context,
                event.userId(),
                event.transactionId()
        );
    }

    public void sendTransactionFailedNotification(UserInfo user, TransactionFailedEvent event) {
        String subject = "Transaction Failed - " + event.type();
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("event", event);

        sendAndLog(
                event.transactionId(),
                NotificationType.TRANSACTION_FAILED,
                user.getEmail(),
                subject,
                "transaction-failed",
                context,
                event.userId(),
                event.transactionId()
        );
    }

    public void sendAccountBlockedNotification(UserInfo user, UserBlockedEvent event) {
        String subject = "Important: Your Account Has Been Blocked";
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("event", event);

        sendAndLog(
                event.triggeredByTransactionId(),
                NotificationType.BLOCKED,
                user.getEmail(),
                subject,
                "account-blocked",
                context,
                event.userId(),
                event.triggeredByTransactionId()
        );
    }

    public void sendFraudAlertToAdmin(FraudAlertEvent event) {
        String subject = "Fraud Alert - Risk Score: " + event.riskScore();
        Context context = new Context();
        context.setVariable("event", event);

        sendAndLog(
                event.analysisId(),
                NotificationType.FRAUD_ALERT,
                adminEmail,
                subject,
                "fraud-alert",
                context,
                null,
                event.transactionId()
        );
    }

    private void sendAndLog(
            UUID eventId,
            NotificationType type,
            String recipientEmail,
            String subject,
            String templateName,
            Context context,
            UUID sourceUserId,
            UUID sourceTransactionId
    ) {
        String body = templateEngine.process(templateName, context);

        try {
            sendEmail(recipientEmail, subject, body);

            notificationLogRepository.save(NotificationLog.builder()
                    .eventId(eventId)
                    .notificationType(type)
                    .recipientEmail(recipientEmail)
                    .subject(subject)
                    .status(NotificationStatus.SENT)
                    .sentAt(Instant.now())
                    .sourceUserId(sourceUserId)
                    .sourceTransactionId(sourceTransactionId)
                    .build());

            meterRegistry.counter("notification.sent", "type", type.name()).increment();
            log.info("Sent {} notification to {}", type, recipientEmail);

        } catch (Exception e) {
            notificationLogRepository.save(NotificationLog.builder()
                    .eventId(eventId)
                    .notificationType(type)
                    .recipientEmail(recipientEmail)
                    .subject(subject)
                    .status(NotificationStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .sentAt(Instant.now())
                    .sourceUserId(sourceUserId)
                    .sourceTransactionId(sourceTransactionId)
                    .build());

            meterRegistry.counter("notification.failed", "type", type.name(), "reason", e.getClass().getSimpleName()).increment();
            log.error("Failed to send {} notification to {}: {}", type, recipientEmail, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private void sendEmail(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        try {
            helper.setFrom(fromEmail, fromName);
        } catch (java.io.UnsupportedEncodingException e) {
            helper.setFrom(fromEmail);
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        mailSender.send(message);
    }
}
