package com.patorinaldi.wallet.account.service;

import com.patorinaldi.wallet.account.dto.CreateWalletRequest;
import com.patorinaldi.wallet.account.dto.WalletResponse;
import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.entity.Wallet;
import com.patorinaldi.wallet.account.mapper.WalletMapper;
import com.patorinaldi.wallet.account.repository.UserRepository;
import com.patorinaldi.wallet.account.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletMapper walletMapper;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User doesn't exists"));

        Wallet wallet = Wallet.builder()
                .user(user)
                .currency(request.currency() != null ? request.currency() : "USD")
                .build();

        if (walletRepository.existsByUserIdAndCurrency(wallet.getUser().getId(), wallet.getCurrency())) {
            throw new IllegalStateException("User already have an " + request.currency() + " wallet");
        }

        return walletMapper.toResponse(walletRepository.save(wallet));
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletById(UUID id) {

        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id not found"));

        return walletMapper.toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getAllByUserId(UUID userId) {
        return walletRepository.findByUserId(userId).stream()
                .map(walletMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getActiveByUserId(UUID userId) {
        return walletRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(walletMapper::toResponse)
                .toList();
    }

    @Transactional
    public WalletResponse deactivateWallet(UUID id) {
        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id not found"));
        if (wallet.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Balance must be 0");
        }
        wallet.setActive(false);
        return walletMapper.toResponse(wallet);
    }
}
