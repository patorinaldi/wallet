package com.patorinaldi.wallet.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.enums.TransactionStatus;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    private UUID walletId;

    private UUID userId;

    @Column(name = "related_wallet_id")
    private UUID relatedWalletId;

    @Column(name = "related_transaction_id")
    private UUID relatedTransactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String currency;

    @Column(precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private String description;

    private String errorMessage;

    public void complete(BigDecimal newBalance) {
        this.status = TransactionStatus.COMPLETED;
        this.balanceAfter = newBalance;
        this.completedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = TransactionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }
}
