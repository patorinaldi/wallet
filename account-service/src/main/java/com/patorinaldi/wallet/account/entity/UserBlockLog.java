package com.patorinaldi.wallet.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_block_logs",
        indexes = @Index(name = "idx_block_log_transaction",
                columnList = "triggeredByTransactionId", unique = true))
public class UserBlockLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private UUID triggeredByTransactionId;

    private String reason;
    private Integer riskScore;
    private Instant blockedAt;

    @CreationTimestamp
    private Instant createdAt;

}