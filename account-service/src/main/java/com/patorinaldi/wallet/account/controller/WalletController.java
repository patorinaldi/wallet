package com.patorinaldi.wallet.account.controller;

import com.patorinaldi.wallet.account.dto.CreateWalletRequest;
import com.patorinaldi.wallet.account.dto.WalletResponse;
import com.patorinaldi.wallet.account.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/wallets")
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@RequestBody @Valid CreateWalletRequest createWalletRequest) {
        log.info("POST /wallets - Creating wallet");
        return walletService.createWallet(createWalletRequest);
    }

    @DeleteMapping("/wallets/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse deactivateWallet(@PathVariable UUID id) {
        log.info("DELETE /wallets/{}/deactivate - Deactivating wallet", id);
        return walletService.deactivateWallet(id);
    }

    @GetMapping("/wallets/{id}")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse getWalletById(@PathVariable UUID id) {
        log.debug("GET /wallets/{} - Fetching wallet", id);
        return walletService.getWalletById(id);
    }

    @GetMapping("/users/{userId}/wallets")
    @ResponseStatus(HttpStatus.OK)
    public List<WalletResponse> getAllByUserId(@PathVariable UUID userId) {
        log.debug("GET /users/{}/wallets - Fetching all wallets", userId);
        return walletService.getAllByUserId(userId);
    }

    @GetMapping("/users/{userId}/wallets/active")
    @ResponseStatus(HttpStatus.OK)
    public List<WalletResponse> getActiveByUserId(@PathVariable UUID userId) {
        log.debug("GET /users/{}/wallets/active - Fetching active wallets", userId);
        return walletService.getActiveByUserId(userId);
    }
}
