package com.patorinaldi.wallet.transaction.client;

public interface FraudClient {

    /**
     * Performs a synchronous fraud check before processing a transaction.
     *
     * @param request the fraud check request containing transaction details
     * @return the fraud check response with decision and risk score
     */
    FraudCheckResponse checkTransaction(FraudCheckRequest request);
}
