package com.patorinaldi.wallet.account.service;

import com.patorinaldi.wallet.account.dto.UpdateUserRequest;
import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.mapper.UserMapper;
import com.patorinaldi.wallet.account.repository.UserRepository;
import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.account.dto.UserResponse;
import com.patorinaldi.wallet.account.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse createUser(CreateUserRequest createUserRequest) {

        if (userRepository.existsByEmail(createUserRequest.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User
                .builder()
                .email(createUserRequest.email())
                .fullName(createUserRequest.fullName())
                .phoneNumber(createUserRequest.phoneNumber())
                .build();
        user = userRepository.saveAndFlush(user);

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

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id not found"));

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email not found"));

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.email() != null) {
            if (userRepository.existsByEmail(request.email())) throw new IllegalArgumentException("Email already exists");
            user.setEmail(request.email());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isActive()) {
            throw new IllegalStateException("User is already deactivated");
        }

        user.setActive(false);

        return userMapper.toResponse(userRepository.save(user));
    }
}
