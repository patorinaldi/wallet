package com.patorinaldi.wallet.fraud.controller;

import com.patorinaldi.wallet.fraud.dto.FraudCheckRequest;
import com.patorinaldi.wallet.fraud.dto.FraudCheckResponse;
import com.patorinaldi.wallet.fraud.service.SyncFraudCheckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudCheckController {

    private final SyncFraudCheckService syncFraudCheckService;

    @PostMapping("/check")
    public ResponseEntity<FraudCheckResponse> checkTransaction(
            @Valid @RequestBody FraudCheckRequest request) {

        log.info("Received fraud check request for wallet: {}, amount: {}",
                request.walletId(), request.amount());

        FraudCheckResponse response = syncFraudCheckService.checkTransaction(request);

        log.info("Fraud check response for wallet: {}: decision={}, riskScore={}",
                request.walletId(), response.decision(), response.riskScore());

        return ResponseEntity.ok(response);
    }
}
