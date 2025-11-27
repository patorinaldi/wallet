package com.patorinaldi.wallet.account.service;

import com.patorinaldi.wallet.account.dto.CreateWalletRequest;
import com.patorinaldi.wallet.account.dto.WalletResponse;
import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.entity.Wallet;
import com.patorinaldi.wallet.account.mapper.WalletMapper;
import com.patorinaldi.wallet.account.repository.UserRepository;
import com.patorinaldi.wallet.account.repository.WalletRepository;
import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WalletService walletService;

    @Test
    void createWallet_shouldCreateWalletWithDefaultCurrency_whenCurrencyIsNull() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateWalletRequest request = new CreateWalletRequest(userId, null);

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Test User")
                .build();

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .user(user)
                .currency("USD")
                .active(true)
                .createdAt(Instant.now())
                .build();

        WalletResponse expectedResponse = new WalletResponse(
                wallet.getId(),
                userId,
                wallet.getCreatedAt(),
                true,
                "USD"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.existsByUserIdAndCurrency(userId, "USD")).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
        when(walletMapper.toResponse(wallet)).thenReturn(expectedResponse);

        // When
        WalletResponse actualResponse = walletService.createWallet(request);

        // Then
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
        assertEquals("USD", actualResponse.currency());
        verify(userRepository).findById(userId);
        verify(walletRepository).existsByUserIdAndCurrency(userId, "USD");
        verify(walletRepository).save(any(Wallet.class));
        verify(eventPublisher).publishEvent(any(WalletCreatedEvent.class));
        verify(walletMapper).toResponse(wallet);
    }

    @Test
    void createWallet_shouldCreateWalletWithCustomCurrency_whenCurrencyIsProvided() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateWalletRequest request = new CreateWalletRequest(userId, "EUR");

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Test User")
                .build();

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .user(user)
                .currency("EUR")
                .active(true)
                .createdAt(Instant.now())
                .build();

        WalletResponse expectedResponse = new WalletResponse(
                wallet.getId(),
                userId,
                wallet.getCreatedAt(),
                true,
                "EUR"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.existsByUserIdAndCurrency(userId, "EUR")).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
        when(walletMapper.toResponse(wallet)).thenReturn(expectedResponse);

        // When
        WalletResponse actualResponse = walletService.createWallet(request);

        // Then
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
        assertEquals("EUR", actualResponse.currency());
        verify(userRepository).findById(userId);
        verify(walletRepository).existsByUserIdAndCurrency(userId, "EUR");
        verify(walletRepository).save(any(Wallet.class));
        verify(eventPublisher).publishEvent(any(WalletCreatedEvent.class));
        verify(walletMapper).toResponse(wallet);
    }

    @Test
    void createWallet_shouldThrowException_whenUserDoesNotExist() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateWalletRequest request = new CreateWalletRequest(userId, "USD");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletService.createWallet(request)
        );

        assertEquals("User doesn't exists", exception.getMessage());
        verify(userRepository).findById(userId);
        verify(walletRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createWallet_shouldThrowException_whenUserAlreadyHasWalletWithSameCurrency() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateWalletRequest request = new CreateWalletRequest(userId, "USD");

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Test User")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.existsByUserIdAndCurrency(userId, "USD")).thenReturn(true);

        // When & Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> walletService.createWallet(request)
        );

        assertEquals("User already have an USD wallet", exception.getMessage());
        verify(userRepository).findById(userId);
        verify(walletRepository).existsByUserIdAndCurrency(userId, "USD");
        verify(walletRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getWalletById_shouldReturnWalletResponse_whenWalletExists() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Test User")
                .build();

        Wallet wallet = Wallet.builder()
                .id(walletId)
                .user(user)
                .currency("USD")
                .active(true)
                .createdAt(Instant.now())
                .build();

        WalletResponse expectedResponse = new WalletResponse(
                walletId,
                userId,
                wallet.getCreatedAt(),
                true,
                "USD"
        );

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletMapper.toResponse(wallet)).thenReturn(expectedResponse);

        // When
        WalletResponse actualResponse = walletService.getWalletById(walletId);

        // Then
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
        verify(walletRepository).findById(walletId);
        verify(walletMapper).toResponse(wallet);
    }

    @Test
    void getWalletById_shouldThrowException_whenWalletDoesNotExist() {
        // Given
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletService.getWalletById(walletId)
        );

        assertEquals("Id not found", exception.getMessage());
        verify(walletRepository).findById(walletId);
        verifyNoInteractions(walletMapper);
    }

    @Test
    void getAllByUserId_shouldReturnListOfWallets_whenUserHasWallets() {
        // Given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Test User")
                .build();

        Wallet wallet1 = Wallet.builder()
                .id(UUID.randomUUID())
                .user(user)
                .currency("USD")
                .active(true)
                .createdAt(Instant.now())
                .build();

        Wallet wallet2 = Wallet.builder()
                .id(UUID.randomUUID())
                .user(user)
                .currency("EUR")
                .active(false)
                .createdAt(Instant.now())
                .build();

        List<Wallet> wallets = List.of(wallet1, wallet2);

        WalletResponse response1 = new WalletResponse(
                wallet1.getId(),
                userId,
                wallet1.getCreatedAt(),
                true,
                "USD"
        );

        WalletResponse response2 = new WalletResponse(
                wallet2.getId(),
                userId,
                wallet2.getCreatedAt(),
                false,
                "EUR"
        );

        when(walletRepository.findByUserId(userId)).thenReturn(wallets);
        when(walletMapper.toResponse(wallet1)).thenReturn(response1);
        when(walletMapper.toResponse(wallet2)).thenReturn(response2);

        // When
        List<WalletResponse> actualResponses = walletService.getAllByUserId(userId);

        // Then
        assertNotNull(actualResponses);
        assertEquals(2, actualResponses.size());
        assertThat(actualResponses).containsExactly(response1, response2);
        verify(walletRepository).findByUserId(userId);
        verify(walletMapper, times(2)).toResponse(any(Wallet.class));
    }

    @Test
    void getAllByUserId_shouldReturnEmptyList_whenUserHasNoWallets() {
        // Given
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // When
        List<WalletResponse> actualResponses = walletService.getAllByUserId(userId);

        // Then
        assertNotNull(actualResponses);
        assertTrue(actualResponses.isEmpty());
        verify(walletRepository).findByUserId(userId);
        verifyNoInteractions(walletMapper);
    }

    @Test
    void getActiveByUserId_shouldReturnOnlyActiveWallets_whenUserHasActiveWallets() {
        // Given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Test User")
                .build();

        Wallet activeWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .user(user)
                .currency("USD")
                .active(true)
                .createdAt(Instant.now())
                .build();

        List<Wallet> activeWallets = List.of(activeWallet);

        WalletResponse response = new WalletResponse(
                activeWallet.getId(),
                userId,
                activeWallet.getCreatedAt(),
                true,
                "USD"
        );

        when(walletRepository.findByUserIdAndActiveTrue(userId)).thenReturn(activeWallets);
        when(walletMapper.toResponse(activeWallet)).thenReturn(response);

        // When
        List<WalletResponse> actualResponses = walletService.getActiveByUserId(userId);

        // Then
        assertNotNull(actualResponses);
        assertEquals(1, actualResponses.size());
        assertTrue(actualResponses.getFirst().active());
        verify(walletRepository).findByUserIdAndActiveTrue(userId);
        verify(walletMapper).toResponse(activeWallet);
    }

    @Test
    void getActiveByUserId_shouldReturnEmptyList_whenUserHasNoActiveWallets() {
        // Given
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserIdAndActiveTrue(userId)).thenReturn(Collections.emptyList());

        // When
        List<WalletResponse> actualResponses = walletService.getActiveByUserId(userId);

        // Then
        assertNotNull(actualResponses);
        assertTrue(actualResponses.isEmpty());
        verify(walletRepository).findByUserIdAndActiveTrue(userId);
        verifyNoInteractions(walletMapper);
    }

    @Test
    void deactivateWallet_shouldDeactivateWallet_whenWalletExists() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Test User")
                .build();

        Wallet activeWallet = Wallet.builder()
                .id(walletId)
                .user(user)
                .currency("USD")
                .active(true)
                .createdAt(Instant.now())
                .build();

        Wallet deactivatedWallet = Wallet.builder()
                .id(walletId)
                .user(user)
                .currency("USD")
                .active(false)
                .createdAt(activeWallet.getCreatedAt())
                .build();

        WalletResponse expectedResponse = new WalletResponse(
                walletId,
                userId,
                deactivatedWallet.getCreatedAt(),
                false,
                "USD"
        );

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(activeWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(deactivatedWallet);
        when(walletMapper.toResponse(deactivatedWallet)).thenReturn(expectedResponse);

        // When
        WalletResponse actualResponse = walletService.deactivateWallet(walletId);

        // Then
        assertNotNull(actualResponse);
        assertFalse(actualResponse.active());
        assertThat(activeWallet.isActive()).isFalse();
        verify(walletRepository).findById(walletId);
        verify(walletRepository).save(activeWallet);
        verify(walletMapper).toResponse(deactivatedWallet);
    }

    @Test
    void deactivateWallet_shouldThrowException_whenWalletDoesNotExist() {
        // Given
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletService.deactivateWallet(walletId)
        );

        assertEquals("Id not found", exception.getMessage());
        verify(walletRepository).findById(walletId);
        verify(walletRepository, never()).save(any());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void deactivateWallet_shouldThrowException_whenWalletIsAlreadyDeactivated() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Test User")
                .build();

        Wallet inactiveWallet = Wallet.builder()
                .id(walletId)
                .user(user)
                .currency("USD")
                .active(false)
                .createdAt(Instant.now())
                .build();

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(inactiveWallet));

        // When & Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> walletService.deactivateWallet(walletId)
        );

        assertEquals("Wallet is already deactivated", exception.getMessage());
        verify(walletRepository).findById(walletId);
        verify(walletRepository, never()).save(any());
        verifyNoInteractions(walletMapper);
    }
}
