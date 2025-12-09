package com.patorinaldi.wallet.fraud.entity;

import com.patorinaldi.wallet.common.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fraud_analysis",
       indexes = @Index(name = "idx_fraud_transaction", columnList = "transactionId", unique = true))
public class FraudAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "transaction_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @ElementCollection
    @CollectionTable(name = "fraud_triggered_rules", joinColumns = @JoinColumn(name = "fraud_analysis_id"))
    @Column(name = "rule")
    private List<String> triggeredRules;

    @Column(name = "decision", nullable = false)
    @Enumerated(EnumType.STRING)
    private FraudDecision decision;

    private Instant analyzedAt;

    private String notes;

}
