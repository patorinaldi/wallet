package com.patorinaldi.wallet.account.service;

import com.patorinaldi.wallet.account.dto.UpdateUserRequest;
import com.patorinaldi.wallet.account.dto.UserStatusResponse;
import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.entity.UserBlockLog;
import com.patorinaldi.wallet.account.entity.UserStatus;
import com.patorinaldi.wallet.account.exception.EmailAlreadyExistsException;
import com.patorinaldi.wallet.account.exception.UserAlreadyDeactivatedException;
import com.patorinaldi.wallet.account.exception.UserNotFoundException;
import com.patorinaldi.wallet.account.mapper.UserMapper;
import com.patorinaldi.wallet.account.repository.UserBlockLogRepository;
import com.patorinaldi.wallet.account.repository.UserRepository;
import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.account.dto.UserResponse;
import com.patorinaldi.wallet.account.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;
    private final UserBlockLogRepository userBlockLogRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest createUserRequest) {
        log.info("Creating user with email: {}", createUserRequest.email());

        if (userRepository.existsByEmail(createUserRequest.email())) {
            log.warn("Email already registered: {}", createUserRequest.email());
            throw new EmailAlreadyExistsException(createUserRequest.email());
        }

        User user = User
                .builder()
                .email(createUserRequest.email())
                .fullName(createUserRequest.fullName())
                .phoneNumber(createUserRequest.phoneNumber())
                .build();
        user = userRepository.saveAndFlush(user);

        log.info("User created successfully with ID: {}", user.getId());

        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getCreatedAt());
        eventPublisher.publishEvent(event);

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        log.debug("Fetching user by ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", id);
                    return new UserNotFoundException(id);
                });

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new UserNotFoundException(email);
                });

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found for update, ID: {}", id);
                    return new UserNotFoundException(id);
                });

        if (request.email() != null) {
            if (userRepository.existsByEmail(request.email())) {
                log.warn("Cannot update user {}, email already exists: {}", id, request.email());
                throw new EmailAlreadyExistsException(request.email());
            }
            user.setEmail(request.email());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }

        log.info("User updated successfully: {}", id);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse deactivateUser(UUID id) {
        log.info("Deactivating user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found for deactivation, ID: {}", id);
                    return new UserNotFoundException(id);
                });

        if (!user.isActive()) {
            log.warn("User already deactivated: {}", id);
            throw new UserAlreadyDeactivatedException(id);
        }

        user.setActive(false);

        log.info("User deactivated successfully: {}", id);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void blockUser(UUID userId, UUID triggeredByTransactionId,
                          String reason, Integer riskScore, Instant blockedAt) {
        log.warn("Blocking user {} due to fraud. Transaction: {}, Risk: {}",
                userId, triggeredByTransactionId, riskScore);

        if (userBlockLogRepository.existsByTriggeredByTransactionId(triggeredByTransactionId)) {
            log.info("Block event for transaction {} already processed", triggeredByTransactionId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setStatus(UserStatus.BLOCKED);
        user.setBlockedAt(blockedAt);
        user.setBlockReason(reason);
        user.setBlockedByTransactionId(triggeredByTransactionId);
        userRepository.save(user);

        UserBlockLog userBlockLog = UserBlockLog.builder()
                .userId(userId)
                .triggeredByTransactionId(triggeredByTransactionId)
                .reason(reason)
                .riskScore(riskScore)
                .blockedAt(blockedAt)
                .build();
        userBlockLogRepository.save(userBlockLog);

        log.warn("User {} blocked successfully", userId);
    }


    public UserStatusResponse getUserStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return new UserStatusResponse(
                userId,
                user.getStatus()
                );
    }
}
