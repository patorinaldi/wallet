package com.patorinaldi.wallet.account.event;

import com.patorinaldi.wallet.account.exception.UserNotFoundException;
import com.patorinaldi.wallet.account.service.UserService;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserBlockedEventListener {

    private final UserService userService;

    @KafkaListener(topics = "user-blocked", groupId = "account-service")
    public void handleUserBlocked(UserBlockedEvent event) {
        log.info("Received UserBlockedEvent for user: {}", event.userId());

        try {
            userService.blockUser(
                    event.userId(),
                    event.triggeredByTransactionId(),
                    event.reason(),
                    event.riskScore(),
                    event.blockedAt()
            );
        } catch (UserNotFoundException e) {
            log.error("Cannot block user - not found: {}", event.userId());
            throw e;
        }
    }

}
