package com.patorinaldi.wallet.account.service;

import com.patorinaldi.wallet.account.dto.CreateWalletRequest;
import com.patorinaldi.wallet.account.dto.WalletResponse;
import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.entity.Wallet;
import com.patorinaldi.wallet.account.mapper.WalletMapper;
import com.patorinaldi.wallet.account.repository.UserRepository;
import com.patorinaldi.wallet.account.repository.WalletRepository;
import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletMapper walletMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User doesn't exists"));

        String currency = request.currency() != null ? request.currency() : "USD";

        if (walletRepository.existsByUserIdAndCurrency(request.userId(), currency)) {
            throw new IllegalStateException("User already have an " + currency + " wallet");
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .currency(currency)
                .build();

        wallet = walletRepository.save(wallet);

        WalletCreatedEvent event = new WalletCreatedEvent(
                UUID.randomUUID(),
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getCurrency(),
                wallet.getCreatedAt()
        );
        eventPublisher.publishEvent(event); 

        return walletMapper.toResponse(wallet);
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

        if (!wallet.isActive()) {
            throw new IllegalStateException("Wallet is already deactivated");
        }

        wallet.setActive(false);
        Wallet deactivatedWallet = walletRepository.save(wallet);
        return walletMapper.toResponse(deactivatedWallet);
    }
}
