package com.patorinaldi.wallet.transaction.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Component
public class FraudClientImpl implements FraudClient {

    private final WebClient webClient;
    private final Duration timeout;

    public FraudClientImpl(
            WebClient.Builder webClientBuilder,
            @Value("${fraud.service.url:http://localhost:8085}") String fraudServiceUrl,
            @Value("${fraud.service.timeout-ms:1000}") long timeoutMs) {

        this.webClient = webClientBuilder
                .baseUrl(fraudServiceUrl)
                .build();
        this.timeout = Duration.ofMillis(timeoutMs);

        log.info("FraudClient initialized with URL: {}, timeout: {}ms", fraudServiceUrl, timeoutMs);
    }

    @Override
    @CircuitBreaker(name = "fraudService", fallbackMethod = "fallbackCheck")
    public FraudCheckResponse checkTransaction(FraudCheckRequest request) {
        log.debug("Calling fraud service for wallet: {}, amount: {}",
                request.walletId(), request.amount());

        try {
            FraudCheckResponse response = webClient.post()
                    .uri("/api/fraud/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(FraudCheckResponse.class)
                    .timeout(timeout)
                    .block();

            log.info("Fraud check result for wallet {}: decision={}, riskScore={}",
                    request.walletId(), response.decision(), response.riskScore());

            return response;
        } catch (WebClientResponseException e) {
            log.error("Fraud service returned error status {}: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * Fallback method when fraud service is unavailable (circuit breaker open or timeout).
     * Implements fail-open strategy: allows transactions but flags them for manual review.
     */
    @SuppressWarnings("unused")
    private FraudCheckResponse fallbackCheck(FraudCheckRequest request, Throwable throwable) {
        log.warn("Fraud service unavailable for wallet {}. Applying fail-open with FLAG. Error: {}",
                request.walletId(), throwable.getMessage());

        return new FraudCheckResponse(
                0,
                "FLAG",
                Collections.singletonList("FRAUD_SERVICE_UNAVAILABLE"),
                "Fraud service unavailable - transaction flagged for manual review"
        );
    }
}
