package com.patorinaldi.wallet.transaction.event;

import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.helper.TestDataBuilder;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletEventListenerTest {

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @InjectMocks
    private WalletEventListener walletEventListener;

    @Test
    void handleWalletCreated_shouldCreateWalletBalance_whenNewWallet() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WalletCreatedEvent event = TestDataBuilder.createWalletCreatedEvent(walletId, userId, "EUR");

        when(walletBalanceRepository.existsByWalletId(walletId)).thenReturn(false);

        // When
        walletEventListener.handleWalletCreated(event);

        // Then
        verify(walletBalanceRepository).existsByWalletId(walletId);

        ArgumentCaptor<WalletBalance> captor = ArgumentCaptor.forClass(WalletBalance.class);
        verify(walletBalanceRepository).save(captor.capture());

        WalletBalance savedBalance = captor.getValue();
        assertEquals(walletId, savedBalance.getWalletId());
        assertEquals(userId, savedBalance.getUserId());
        assertEquals("EUR", savedBalance.getCurrency());
        assertEquals(BigDecimal.ZERO, savedBalance.getBalance());
    }

    @Test
    void handleWalletCreated_shouldSkipCreation_whenBalanceAlreadyExists() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WalletCreatedEvent event = TestDataBuilder.createWalletCreatedEvent(walletId, userId, "EUR");

        when(walletBalanceRepository.existsByWalletId(walletId)).thenReturn(true);

        // When
        walletEventListener.handleWalletCreated(event);

        // Then
        verify(walletBalanceRepository).existsByWalletId(walletId);
        verify(walletBalanceRepository, never()).save(any(WalletBalance.class));
    }

    @Test
    void handleWalletCreated_shouldSetCorrectInitialValues() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String currency = "USD";
        WalletCreatedEvent event = TestDataBuilder.createWalletCreatedEvent(walletId, userId, currency);

        when(walletBalanceRepository.existsByWalletId(walletId)).thenReturn(false);

        // When
        walletEventListener.handleWalletCreated(event);

        // Then
        ArgumentCaptor<WalletBalance> captor = ArgumentCaptor.forClass(WalletBalance.class);
        verify(walletBalanceRepository).save(captor.capture());

        WalletBalance savedBalance = captor.getValue();
        assertEquals(BigDecimal.ZERO, savedBalance.getBalance());
        assertEquals(walletId, savedBalance.getWalletId());
        assertEquals(userId, savedBalance.getUserId());
        assertEquals(currency, savedBalance.getCurrency());
    }
}
