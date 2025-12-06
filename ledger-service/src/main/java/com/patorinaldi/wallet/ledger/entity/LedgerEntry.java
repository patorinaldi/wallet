package com.patorinaldi.wallet.ledger.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ledger_entries",
        indexes = {
                @Index(name = "idx_entry_journal", columnList = "journal_id"),
                @Index(name = "idx_entry_account", columnList = "account_id"),
                @Index(name = "idx_ledger_recorded_at", columnList = "recorded_at")
        })
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id", nullable = false)
    private LedgerJournal journal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private LedgerAccount account;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntrySide side;

    @Column(nullable = false)
    private String currency;

    private String description;

    @Column(precision = 19, scale = 4)
    private BigDecimal reportedBalanceAfter;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "source_event", nullable = false)
    private String sourceEvent;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

}
