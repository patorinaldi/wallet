package com.patorinaldi.wallet.account.controller;

import com.patorinaldi.wallet.account.dto.CreateWalletRequest;
import com.patorinaldi.wallet.account.dto.WalletResponse;
import com.patorinaldi.wallet.account.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/wallets")
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@RequestBody @Valid CreateWalletRequest createWalletRequest) {
        return walletService.createWallet(createWalletRequest);
    }

    @DeleteMapping("/wallets/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse deactivateWallet(@PathVariable UUID id) {
        return walletService.deactivateWallet(id);
    }

    @GetMapping("/wallets/{id}")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse getWalletById(@PathVariable UUID id) {
        return walletService.getWalletById(id);
    }

    @GetMapping("/users/{userId}/wallets")
    @ResponseStatus(HttpStatus.OK)
    public List<WalletResponse> getAllByUserId(@PathVariable UUID userId) {
        return walletService.getAllByUserId(userId);
    }

    @GetMapping("/users/{userId}/wallets/active")
    @ResponseStatus(HttpStatus.OK)
    public List<WalletResponse> getActiveByUserId(@PathVariable UUID userId) {
        return walletService.getActiveByUserId(userId);
    }
}
