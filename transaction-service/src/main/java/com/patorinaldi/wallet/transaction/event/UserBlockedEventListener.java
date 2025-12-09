package com.patorinaldi.wallet.transaction.event;

import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import com.patorinaldi.wallet.transaction.entity.BlockedUser;
import com.patorinaldi.wallet.transaction.repository.BlockedUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserBlockedEventListener {

    private final BlockedUserRepository blockedUserRepository;

    @KafkaListener(topics = "user-blocked", groupId = "transaction-service")
    @Transactional
    public void handleUserBlocked(UserBlockedEvent event) {

        log.info("Received UserBlockedEvent for user: {}", event.userId());

        if(blockedUserRepository.existsByTriggeredByTransactionId(event.triggeredByTransactionId())) {
            log.info("UserBlockedEvent for transaction {} already processed",
                    event.triggeredByTransactionId());
            return;
        }

        BlockedUser blockedUser = BlockedUser.builder()
                .userId(event.userId())
                .triggeredByTransactionId(event.triggeredByTransactionId())
                .reason(event.reason())
                .riskScore(event.riskScore())
                .blockedAt(event.blockedAt())
                .build();

        blockedUserRepository.save(blockedUser);

        log.warn("User {} added to blocked list. Reason: {}", event.userId(), event.reason());

    }
}
