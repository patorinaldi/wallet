package com.patorinaldi.wallet.account.service;

import com.patorinaldi.wallet.account.dto.UpdateUserRequest;
import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.mapper.UserMapper;
import com.patorinaldi.wallet.account.repository.UserRepository;
import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.account.dto.UserResponse;
import com.patorinaldi.wallet.account.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse createUser(CreateUserRequest createUserRequest) {
        log.info("Creating user with email: {}", createUserRequest.email());

        if (userRepository.existsByEmail(createUserRequest.email())) {
            log.warn("Email already registered: {}", createUserRequest.email());
            throw new IllegalArgumentException("Email already registered");
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
                    return new IllegalArgumentException("Id not found");
                });

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new IllegalArgumentException("Email not found");
                });

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found for update, ID: {}", id);
                    return new IllegalArgumentException("User not found");
                });

        if (request.email() != null) {
            if (userRepository.existsByEmail(request.email())) {
                log.warn("Cannot update user {}, email already exists: {}", id, request.email());
                throw new IllegalArgumentException("Email already exists");
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
                    return new IllegalArgumentException("User not found");
                });

        if (!user.isActive()) {
            log.warn("User already deactivated: {}", id);
            throw new IllegalStateException("User is already deactivated");
        }

        user.setActive(false);

        log.info("User deactivated successfully: {}", id);

        return userMapper.toResponse(userRepository.save(user));
    }
}
