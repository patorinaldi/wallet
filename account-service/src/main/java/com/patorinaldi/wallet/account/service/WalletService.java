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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletMapper walletMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        log.info("Creating wallet for user: {}, currency: {}", request.userId(), request.currency());

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> {
                    log.error("User not found for wallet creation: {}", request.userId());
                    return new IllegalArgumentException("User doesn't exists");
                });

        String currency = request.currency() != null ? request.currency() : "USD";

        if (walletRepository.existsByUserIdAndCurrency(request.userId(), currency)) {
            log.warn("User {} already has a {} wallet", request.userId(), currency);
            throw new IllegalStateException("User already have an " + currency + " wallet");
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .currency(currency)
                .build();

        wallet = walletRepository.save(wallet);

        log.info("Wallet created successfully with ID: {}", wallet.getId());

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
        log.debug("Fetching wallet by ID: {}", id);

        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Wallet not found with ID: {}", id);
                    return new IllegalArgumentException("Id not found");
                });

        return walletMapper.toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getAllByUserId(UUID userId) {
        log.debug("Fetching all wallets for user: {}", userId);
        return walletRepository.findByUserId(userId).stream()
                .map(walletMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getActiveByUserId(UUID userId) {
        log.debug("Fetching active wallets for user: {}", userId);
        return walletRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(walletMapper::toResponse)
                .toList();
    }

    @Transactional
    public WalletResponse deactivateWallet(UUID id) {
        log.info("Deactivating wallet with ID: {}", id);

        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Wallet not found for deactivation, ID: {}", id);
                    return new IllegalArgumentException("Id not found");
                });

        if (!wallet.isActive()) {
            log.warn("Wallet already deactivated: {}", id);
            throw new IllegalStateException("Wallet is already deactivated");
        }

        wallet.setActive(false);
        Wallet deactivatedWallet = walletRepository.save(wallet);

        log.info("Wallet deactivated successfully: {}", id);

        return walletMapper.toResponse(deactivatedWallet);
    }
}
