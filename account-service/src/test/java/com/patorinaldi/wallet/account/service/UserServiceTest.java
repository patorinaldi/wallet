package com.patorinaldi.wallet.account.service;

import com.patorinaldi.wallet.account.dto.CreateUserRequest;
import com.patorinaldi.wallet.account.dto.UpdateUserRequest;
import com.patorinaldi.wallet.account.dto.UserResponse;
import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.mapper.UserMapper;
import com.patorinaldi.wallet.account.repository.UserRepository;
import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private UserService userService;

    @Test
    void createUser_shouldCreateUserAndSendEvent_whenEmailIsNotRegistered() {
        // Given
        CreateUserRequest request = new CreateUserRequest("john.doe@example.com", "John Doe", "1234567890");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("john.doe@example.com")
                .fullName("John Doe")
                .phoneNumber("1234567890")
                .createdAt(Instant.now())
                .build();
        UserResponse expectedResponse = new UserResponse(user.getId(), "John Doe", "john.doe@example.com", "1234567890", user.getCreatedAt(), true);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        // When
        UserResponse actualResponse = userService.createUser(request);

        // Then
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
        verify(userRepository).existsByEmail(request.email());
        verify(userRepository).saveAndFlush(any(User.class));
        verify(kafkaTemplate).send(eq("user-registered"), anyString(), any(UserRegisteredEvent.class));
        verify(userMapper).toResponse(any(User.class));
    }

    @Test
    void createUser_shouldThrowException_whenEmailIsAlreadyRegistered() {
        // Given
        CreateUserRequest request = new CreateUserRequest("john.doe@example.com", "John Doe", "1234567890");
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> userService.createUser(request));
    }

    @Test
    void getUserById_shouldReturnUserResponse_whenUserExists() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("john.doe@example.com")
                .fullName("John Doe")
                .phoneNumber("1234567890")
                .createdAt(Instant.now())
                .build();
        UserResponse expectedResponse = new UserResponse(
                userId,
                "john.doe@example.com",
                "John Doe",
                "1234567890",
                user.getCreatedAt(),
                true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);
        // When
        UserResponse actualResponse = userService.getUserById(userId);
        // Then
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
        verify(userRepository).findById(userId);
        verify(userMapper).toResponse(user);
    }

    @Test
    void getUserById_shouldThrowException_whenUserDoesNotExist() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> userService.getUserById(userId));

        verify(userRepository).findById(userId);
        verifyNoInteractions(userMapper);

    }

    @Test
    void getUserByEmail_shouldReturnUserResponse_whenUserExists() {
        // Given
        String email = "john.doe@example.com";

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .fullName("John Doe")
                .phoneNumber("1234567890")
                .createdAt(Instant.now())
                .build();

        UserResponse expectedResponse = new UserResponse(
                user.getId(),
                email,
                "John Doe",
                "1234567890",
                user.getCreatedAt(),
                true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        // When
        UserResponse actualResponse = userService.getUserByEmail(email);

        // Then
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
        verify(userRepository).findByEmail(email);
        verify(userMapper).toResponse(user);
    }

    @Test
    void getUserByEmail_shouldThrowException_whenUserDoesNotExist() {
        // Given
        String email = "non.existent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> userService.getUserByEmail(email));

        verify(userRepository).findByEmail(email);
        verifyNoInteractions(userMapper);
    }

    @Test
    void updateUser_shouldUpdateUser_whenUserExists() {
        // Given
        UUID userId = UUID.randomUUID();
        UpdateUserRequest request = new UpdateUserRequest("jane.doe@example.com", "Jane Doe", "0987654321");

        User existingUser = User.builder()
                .id(userId)
                .email("john.doe@example.com")
                .fullName("John Doe")
                .phoneNumber("1234567890")
                .createdAt(Instant.now())
                .active(true)
                .build();

        User updatedUser = User.builder()
                .id(userId)
                .email(request.email())
                .fullName(request.fullName())
                .phoneNumber(request.phoneNumber())
                .createdAt(existingUser.getCreatedAt())
                .active(true)
                .build();

        UserResponse expectedResponse = new UserResponse(userId, request.email(), request.fullName(), request.phoneNumber(), updatedUser.getCreatedAt(), true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        when(userMapper.toResponse(updatedUser)).thenReturn(expectedResponse);

        // When
        UserResponse actualResponse = userService.updateUser(userId, request);

        // Then
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);

        verify(userRepository).findById(userId);
        verify(userRepository).existsByEmail(request.email());
        verify(userRepository).save(any(User.class));
        verify(userMapper).toResponse(updatedUser);
    }

    @Test
    void updateUser_shouldThrowException_whenUserNotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        UpdateUserRequest request = new UpdateUserRequest("jane.doe@example.com", "Jane Doe", null);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> userService.updateUser(userId, request));
        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenEmailAlreadyExists() {
        // Given
        UUID userId = UUID.randomUUID();
        UpdateUserRequest request = new UpdateUserRequest("existing.email@example.com", "Jane Doe", null);
        User existingUser = User.builder().id(userId).email("original@example.com").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> userService.updateUser(userId, request));
        verify(userRepository).findById(userId);
        verify(userRepository).existsByEmail(request.email());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_shouldDeactivateUser_whenUserIsActive() {
        // Given
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder().id(userId).active(true).build();
        User deactivatedUser = User.builder().id(userId).active(false).build();
        UserResponse expectedResponse = new UserResponse(userId, null, null, null, null, false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenReturn(deactivatedUser);
        when(userMapper.toResponse(deactivatedUser)).thenReturn(expectedResponse);

        // When
        UserResponse actualResponse = userService.deactivateUser(userId);

        // Then
        assertThat(actualResponse.active()).isFalse();
        verify(userRepository).findById(userId);
        verify(userRepository).save(activeUser);
        assertThat(activeUser.isActive()).isFalse();
    }

    @Test
    void deactivateUser_shouldThrowException_whenUserNotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> userService.deactivateUser(userId));
    }

    @Test
    void deactivateUser_shouldThrowException_whenUserIsAlreadyDeactivated() {
        // Given
        UUID userId = UUID.randomUUID();
        User inactiveUser = User.builder().id(userId).active(false).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(inactiveUser));

        // When & Then
        assertThrows(IllegalStateException.class, () -> userService.deactivateUser(userId));
    }
}
