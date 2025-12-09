package com.patorinaldi.wallet.fraud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fraud_rules")
public class FraudRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_code", nullable = false, unique = true)
    private String ruleCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "rule_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;

    @Column(precision = 19, scale = 4)
    private BigDecimal threshold;

    @Column(name = "score_impact", nullable = false)
    private Integer scoreImpact;

    @Column(name = "time_window_minutes")
    private Integer timeWindowMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
