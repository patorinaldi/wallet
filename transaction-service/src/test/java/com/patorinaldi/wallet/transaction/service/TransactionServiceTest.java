package com.patorinaldi.wallet.transaction.service;

import com.patorinaldi.wallet.common.enums.TransactionStatus;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.transaction.dto.DepositRequest;
import com.patorinaldi.wallet.transaction.dto.TransactionResponse;
import com.patorinaldi.wallet.transaction.dto.TransferRequest;
import com.patorinaldi.wallet.transaction.dto.WithdrawalRequest;
import com.patorinaldi.wallet.transaction.entity.Transaction;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.exception.DuplicateTransactionException;
import com.patorinaldi.wallet.transaction.exception.InsufficientBalanceException;
import com.patorinaldi.wallet.transaction.exception.WalletBalanceNotFoundException;
import com.patorinaldi.wallet.transaction.helper.TestDataBuilder;
import com.patorinaldi.wallet.transaction.mapper.TransactionMapper;
import com.patorinaldi.wallet.transaction.repository.TransactionRepository;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private TransactionPersistenceService persistenceService;

    @InjectMocks
    private TransactionService transactionService;

    // ========== DEPOSIT TESTS ==========

    @Test
    void deposit_shouldCreateDepositTransaction_whenWalletExists() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal initialBalance = new BigDecimal("100.00");
        BigDecimal depositAmount = new BigDecimal("50.00");

        WalletBalance walletBalance = TestDataBuilder.createWalletBalance(walletId, userId, initialBalance, "USD");
        DepositRequest request = TestDataBuilder.createDepositRequest(walletId, depositAmount, "Test deposit");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(walletId)).thenReturn(Optional.of(walletBalance));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletBalanceRepository.save(any(WalletBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(mock(TransactionResponse.class));

        // When
        TransactionResponse response = transactionService.deposit(request);

        // Then
        assertNotNull(response);
        verify(transactionRepository).existsByIdempotencyKey(request.idempotencyKey());
        verify(walletBalanceRepository).findByWalletId(walletId);
        verify(transactionRepository).save(any(Transaction.class));
        verify(walletBalanceRepository).save(walletBalance);
        verify(transactionMapper).toResponse(any(Transaction.class));

        assertEquals(new BigDecimal("150.00"), walletBalance.getBalance());
    }

    @Test
    void deposit_shouldThrowDuplicateException_whenIdempotencyKeyExists() {
        // Given
        UUID walletId = UUID.randomUUID();
        DepositRequest request = TestDataBuilder.createDepositRequest(walletId, new BigDecimal("50.00"), "Test");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())).thenReturn(true);

        // When & Then
        assertThrows(DuplicateTransactionException.class, () -> transactionService.deposit(request));

        verify(transactionRepository).existsByIdempotencyKey(request.idempotencyKey());
        verify(walletBalanceRepository, never()).findByWalletId(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deposit_shouldThrowNotFoundException_whenWalletNotFound() {
        // Given
        UUID walletId = UUID.randomUUID();
        DepositRequest request = TestDataBuilder.createDepositRequest(walletId, new BigDecimal("50.00"), "Test");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(walletId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(WalletBalanceNotFoundException.class, () -> transactionService.deposit(request));

        verify(transactionRepository).existsByIdempotencyKey(request.idempotencyKey());
        verify(walletBalanceRepository).findByWalletId(walletId);
        verify(transactionRepository, never()).save(any());
    }

    // ========== WITHDRAWAL TESTS ==========

    @Test
    void withdrawal_shouldCreateWithdrawalTransaction_whenSufficientBalance() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal initialBalance = new BigDecimal("100.00");
        BigDecimal withdrawalAmount = new BigDecimal("30.00");

        WalletBalance walletBalance = TestDataBuilder.createWalletBalance(walletId, userId, initialBalance, "USD");
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(walletId, withdrawalAmount, "Test withdrawal");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(walletId)).thenReturn(Optional.of(walletBalance));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletBalanceRepository.save(any(WalletBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(mock(TransactionResponse.class));

        // When
        TransactionResponse response = transactionService.withdrawal(request);

        // Then
        assertNotNull(response);
        verify(transactionRepository).existsByIdempotencyKey(request.idempotencyKey());
        verify(walletBalanceRepository).findByWalletId(walletId);
        verify(transactionRepository).save(any(Transaction.class));
        verify(walletBalanceRepository).save(walletBalance);

        assertEquals(new BigDecimal("70.00"), walletBalance.getBalance());
    }

    @Test
    void withdrawal_shouldSaveFailedTransaction_whenInsufficientBalance() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal initialBalance = new BigDecimal("50.00");
        BigDecimal withdrawalAmount = new BigDecimal("100.00");

        WalletBalance walletBalance = TestDataBuilder.createWalletBalance(walletId, userId, initialBalance, "USD");
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(walletId, withdrawalAmount, "Test withdrawal");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(walletId)).thenReturn(Optional.of(walletBalance));

        // When & Then
        assertThrows(InsufficientBalanceException.class, () -> transactionService.withdrawal(request));

        verify(persistenceService).saveFailedTransaction(argThat(tx ->
                tx.getStatus() == TransactionStatus.FAILED &&
                tx.getErrorMessage() != null
        ));
        verify(walletBalanceRepository, never()).save(any(WalletBalance.class));

        assertEquals(initialBalance, walletBalance.getBalance());
    }

    @Test
    void withdrawal_shouldThrowDuplicateException_whenIdempotencyKeyExists() {
        // Given
        UUID walletId = UUID.randomUUID();
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(walletId, new BigDecimal("30.00"), "Test");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())).thenReturn(true);

        // When & Then
        assertThrows(DuplicateTransactionException.class, () -> transactionService.withdrawal(request));

        verify(transactionRepository).existsByIdempotencyKey(request.idempotencyKey());
        verify(walletBalanceRepository, never()).findByWalletId(any());
    }

    @Test
    void withdrawal_shouldThrowNotFoundException_whenWalletNotFound() {
        // Given
        UUID walletId = UUID.randomUUID();
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(walletId, new BigDecimal("30.00"), "Test");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey())).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(walletId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(WalletBalanceNotFoundException.class, () -> transactionService.withdrawal(request));

        verify(walletBalanceRepository).findByWalletId(walletId);
        verify(transactionRepository, never()).save(any());
    }

    // ========== TRANSFER TESTS ==========

    @Test
    void transfer_shouldCreateTwoLinkedTransactions_whenValid() {
        // Given
        UUID sourceWalletId = UUID.randomUUID();
        UUID destWalletId = UUID.randomUUID();
        UUID sourceUserId = UUID.randomUUID();
        UUID destUserId = UUID.randomUUID();
        BigDecimal sourceBalance = new BigDecimal("200.00");
        BigDecimal destBalance = new BigDecimal("50.00");
        BigDecimal transferAmount = new BigDecimal("75.00");

        WalletBalance sourceWallet = TestDataBuilder.createWalletBalance(sourceWalletId, sourceUserId, sourceBalance, "USD");
        WalletBalance destWallet = TestDataBuilder.createWalletBalance(destWalletId, destUserId, destBalance, "USD");
        TransferRequest request = TestDataBuilder.createTransferRequest(sourceWalletId, destWalletId, transferAmount, "Test transfer");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey() + ":out")).thenReturn(false);
        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey() + ":in")).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(sourceWalletId)).thenReturn(Optional.of(sourceWallet));
        when(walletBalanceRepository.findByWalletId(destWalletId)).thenReturn(Optional.of(destWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
            }
            return tx;
        });
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(mock(TransactionResponse.class));

        // When
        TransactionResponse response = transactionService.transfer(request);

        // Then
        assertNotNull(response);
        verify(transactionRepository).existsByIdempotencyKey(request.idempotencyKey() + ":out");
        verify(transactionRepository).existsByIdempotencyKey(request.idempotencyKey() + ":in");
        verify(walletBalanceRepository).findByWalletId(sourceWalletId);
        verify(walletBalanceRepository).findByWalletId(destWalletId);
        verify(transactionRepository, times(4)).save(any(Transaction.class)); // 2 initial saves + 2 saves with links
        verify(walletBalanceRepository, times(2)).save(any(WalletBalance.class));

        assertEquals(new BigDecimal("125.00"), sourceWallet.getBalance());
        assertEquals(new BigDecimal("125.00"), destWallet.getBalance());
    }

    @Test
    void transfer_shouldSaveFailedTransaction_whenInsufficientBalance() {
        // Given
        UUID sourceWalletId = UUID.randomUUID();
        UUID destWalletId = UUID.randomUUID();
        UUID sourceUserId = UUID.randomUUID();
        UUID destUserId = UUID.randomUUID();
        BigDecimal sourceBalance = new BigDecimal("50.00");
        BigDecimal destBalance = new BigDecimal("100.00");
        BigDecimal transferAmount = new BigDecimal("100.00");

        WalletBalance sourceWallet = TestDataBuilder.createWalletBalance(sourceWalletId, sourceUserId, sourceBalance, "USD");
        WalletBalance destWallet = TestDataBuilder.createWalletBalance(destWalletId, destUserId, destBalance, "USD");
        TransferRequest request = TestDataBuilder.createTransferRequest(sourceWalletId, destWalletId, transferAmount, "Test transfer");

        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(sourceWalletId)).thenReturn(Optional.of(sourceWallet));
        when(walletBalanceRepository.findByWalletId(destWalletId)).thenReturn(Optional.of(destWallet));

        // When & Then
        assertThrows(InsufficientBalanceException.class, () -> transactionService.transfer(request));

        verify(persistenceService).saveFailedTransaction(argThat(tx ->
                tx.getType() == TransactionType.TRANSFER_OUT &&
                tx.getStatus() == TransactionStatus.FAILED
        ));

        assertEquals(sourceBalance, sourceWallet.getBalance());
        assertEquals(destBalance, destWallet.getBalance());
    }

    @Test
    void transfer_shouldThrowDuplicateException_whenEitherKeyExists() {
        // Given
        UUID sourceWalletId = UUID.randomUUID();
        UUID destWalletId = UUID.randomUUID();
        TransferRequest request = TestDataBuilder.createTransferRequest(sourceWalletId, destWalletId, new BigDecimal("50.00"), "Test");

        when(transactionRepository.existsByIdempotencyKey(request.idempotencyKey() + ":out")).thenReturn(true);

        // When & Then
        assertThrows(DuplicateTransactionException.class, () -> transactionService.transfer(request));

        verify(transactionRepository).existsByIdempotencyKey(request.idempotencyKey() + ":out");
        verify(walletBalanceRepository, never()).findByWalletId(any());
    }

    @Test
    void transfer_shouldThrowNotFoundException_whenSourceWalletNotFound() {
        // Given
        UUID sourceWalletId = UUID.randomUUID();
        UUID destWalletId = UUID.randomUUID();
        TransferRequest request = TestDataBuilder.createTransferRequest(sourceWalletId, destWalletId, new BigDecimal("50.00"), "Test");

        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(sourceWalletId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(WalletBalanceNotFoundException.class, () -> transactionService.transfer(request));

        verify(walletBalanceRepository).findByWalletId(sourceWalletId);
    }

    @Test
    void transfer_shouldThrowNotFoundException_whenDestinationWalletNotFound() {
        // Given
        UUID sourceWalletId = UUID.randomUUID();
        UUID destWalletId = UUID.randomUUID();
        UUID sourceUserId = UUID.randomUUID();
        WalletBalance sourceWallet = TestDataBuilder.createWalletBalance(sourceWalletId, sourceUserId, new BigDecimal("200.00"), "USD");
        TransferRequest request = TestDataBuilder.createTransferRequest(sourceWalletId, destWalletId, new BigDecimal("50.00"), "Test");

        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(walletBalanceRepository.findByWalletId(sourceWalletId)).thenReturn(Optional.of(sourceWallet));
        when(walletBalanceRepository.findByWalletId(destWalletId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(WalletBalanceNotFoundException.class, () -> transactionService.transfer(request));

        verify(walletBalanceRepository).findByWalletId(sourceWalletId);
        verify(walletBalanceRepository).findByWalletId(destWalletId);
    }
}
