package com.patorinaldi.wallet.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blocked_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedUser {

    @Id
    private UUID userId;

    @Column(nullable = false, unique = true)
    private UUID triggeredByTransactionId;

    private String reason;
    private Integer riskScore;

    @Column(nullable = false)
    private Instant blockedAt;

    @CreationTimestamp
    private Instant createdAt;

}