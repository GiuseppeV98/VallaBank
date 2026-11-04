
package com.vallabank.backend.transaction;

import com.vallabank.backend.account.Account;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "account_transactions")
public class AccountTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false,
            precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AccountTransaction() {
    }

public AccountTransaction(
        Account account,
        BigDecimal amount,
        BigDecimal balanceAfter
) {
    this(account, "DEPOSIT", amount, balanceAfter);
}

public static AccountTransaction withdrawal(
        Account account,
        BigDecimal amount,
        BigDecimal balanceAfter
) {
    return new AccountTransaction(
            account,
            "WITHDRAWAL",
            amount,
            balanceAfter
    );
}

private AccountTransaction(
        Account account,
        String transactionType,
        BigDecimal amount,
        BigDecimal balanceAfter
) {
    this.account = account;
    this.transactionType = transactionType;
    this.amount = amount;
    this.balanceAfter = balanceAfter;
}


    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
