package com.patorinaldi.wallet.transaction.service;

import com.patorinaldi.wallet.common.enums.TransactionStatus;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.transaction.dto.*;
import com.patorinaldi.wallet.transaction.entity.Transaction;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.exception.DuplicateTransactionException;
import com.patorinaldi.wallet.transaction.exception.InsufficientBalanceException;
import com.patorinaldi.wallet.transaction.exception.TransactionNotFoundException;
import com.patorinaldi.wallet.transaction.exception.WalletBalanceNotFoundException;
import com.patorinaldi.wallet.transaction.helper.TestDataBuilder;
import com.patorinaldi.wallet.transaction.mapper.BalanceMapper;
import com.patorinaldi.wallet.transaction.mapper.TransactionMapper;
import com.patorinaldi.wallet.transaction.repository.TransactionRepository;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
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

    @Mock
    private BalanceMapper balanceMapper;

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

    // ========== QUERY TESTS ==========

    @Test
    void getTransactionsByWallet_shouldReturnPagedTransactions_whenWalletHasTransactions() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        Transaction tx1 = TestDataBuilder.createTransaction(walletId, userId, TransactionType.DEPOSIT, new BigDecimal("100.00"));
        Transaction tx2 = TestDataBuilder.createTransaction(walletId, userId, TransactionType.WITHDRAWAL, new BigDecimal("50.00"));
        Page<Transaction> transactionPage = new PageImpl<>(List.of(tx1, tx2), pageable, 2);

        TransactionResponse response1 = mock(TransactionResponse.class);
        TransactionResponse response2 = mock(TransactionResponse.class);

        when(transactionRepository.findByWalletId(walletId, pageable)).thenReturn(transactionPage);
        when(transactionMapper.toResponse(tx1)).thenReturn(response1);
        when(transactionMapper.toResponse(tx2)).thenReturn(response2);

        // When
        Page<TransactionResponse> result = transactionService.getTransactionsByWallet(walletId, pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        verify(transactionRepository).findByWalletId(walletId, pageable);
        verify(transactionMapper, times(2)).toResponse(any(Transaction.class));
    }

    @Test
    void getTransactionsByWallet_shouldReturnEmptyPage_whenNoTransactions() {
        // Given
        UUID walletId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(transactionRepository.findByWalletId(walletId, pageable)).thenReturn(emptyPage);

        // When
        Page<TransactionResponse> result = transactionService.getTransactionsByWallet(walletId, pageable);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
        verify(transactionRepository).findByWalletId(walletId, pageable);
        verify(transactionMapper, never()).toResponse(any());
    }

    @Test
    void getTransactionById_shouldReturnTransaction_whenExists() {
        // Given
        UUID transactionId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Transaction transaction = TestDataBuilder.createTransaction(walletId, userId, TransactionType.DEPOSIT, new BigDecimal("100.00"));
        transaction.setId(transactionId);

        TransactionResponse expectedResponse = mock(TransactionResponse.class);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(transactionMapper.toResponse(transaction)).thenReturn(expectedResponse);

        // When
        TransactionResponse result = transactionService.getTransactionById(transactionId);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(transactionRepository).findById(transactionId);
        verify(transactionMapper).toResponse(transaction);
    }

    @Test
    void getTransactionById_shouldThrowNotFoundException_whenNotExists() {
        // Given
        UUID transactionId = UUID.randomUUID();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(TransactionNotFoundException.class, () -> transactionService.getTransactionById(transactionId));

        verify(transactionRepository).findById(transactionId);
        verify(transactionMapper, never()).toResponse(any());
    }

    @Test
    void getBalance_shouldReturnBalance_whenWalletExists() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal balance = new BigDecimal("500.00");
        WalletBalance walletBalance = TestDataBuilder.createWalletBalance(walletId, userId, balance, "USD");

        BalanceResponse expectedResponse = mock(BalanceResponse.class);

        when(walletBalanceRepository.findByWalletId(walletId)).thenReturn(Optional.of(walletBalance));
        when(balanceMapper.toResponse(walletBalance)).thenReturn(expectedResponse);

        // When
        BalanceResponse result = transactionService.getBalance(walletId);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(walletBalanceRepository).findByWalletId(walletId);
        verify(balanceMapper).toResponse(walletBalance);
    }

    @Test
    void getBalance_shouldThrowNotFoundException_whenWalletNotFound() {
        // Given
        UUID walletId = UUID.randomUUID();

        when(walletBalanceRepository.findByWalletId(walletId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(WalletBalanceNotFoundException.class, () -> transactionService.getBalance(walletId));

        verify(walletBalanceRepository).findByWalletId(walletId);
        verify(balanceMapper, never()).toResponse(any());
    }
}
