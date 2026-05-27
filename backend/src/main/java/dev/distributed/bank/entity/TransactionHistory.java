package dev.distributed.bank.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity: Lịch sử giao dịch.
 * Ghi lại mọi thao tác: gửi, rút, chuyển tiền.
 *
 * Với chuyển tiền: tạo 2 records
 *   - 1 record TRANSFER_OUT ở tài khoản nguồn
 *   - 1 record TRANSFER_IN ở tài khoản đích
 *
 * Với chuyển tiền liên chi nhánh: tạo 2 records ở 2 site khác nhau
 *   - 1 record INTER_BRANCH_OUT ở site nguồn
 *   - 1 record INTER_BRANCH_IN ở site đích
 */
public class TransactionHistory {

    private Long transactionId;
    private String transactionType;   // DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT,
                                       // INTER_BRANCH_IN, INTER_BRANCH_OUT
    private BigDecimal amount;
    private Long accountId;           // Tài khoản thực hiện
    private Long relatedAccountId;    // Tài khoản đối ứng (nullable)
    private String relatedBranchId;   // Chi nhánh đối ứng (nullable)
    private BigDecimal balanceAfter;  // Snapshot số dư sau giao dịch
    private String status;            // SUCCESS, FAILED, ROLLED_BACK
    private String distributedTxnId;  // Link đến distributed_transaction_log (nullable)
    private String description;
    private LocalDateTime createdAt;

    public TransactionHistory() {
    }

    // Getters & Setters

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getRelatedAccountId() { return relatedAccountId; }
    public void setRelatedAccountId(Long relatedAccountId) { this.relatedAccountId = relatedAccountId; }

    public String getRelatedBranchId() { return relatedBranchId; }
    public void setRelatedBranchId(String relatedBranchId) { this.relatedBranchId = relatedBranchId; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDistributedTxnId() { return distributedTxnId; }
    public void setDistributedTxnId(String distributedTxnId) { this.distributedTxnId = distributedTxnId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
