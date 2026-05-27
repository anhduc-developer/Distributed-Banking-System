package dev.distributed.bank.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity: Log giao dịch phân tán (2PC).
 * Coordinator ghi bảng này để theo dõi trạng thái 2-Phase Commit.
 *
 * Status lifecycle:
 *   Thành công: STARTED → PREPARING → COMMITTING → COMMITTED
 *   Thất bại:   STARTED → PREPARING → ABORTING → ABORTED
 *
 * Bảng này là "nhật ký" — nếu coordinator crash, khi restart
 * có thể đọc lại để recovery.
 */
public class DistributedTransactionLog {

    private String txnId;             // PK: UUID (e.g., "TXN-20240115-abc123")
    private String txnType;           // "INTER_BRANCH_TRANSFER"
    private String status;            // STARTED/PREPARING/COMMITTING/COMMITTED/ABORTING/ABORTED
    private String sourceBranch;      // "HN"
    private String destBranch;        // "HCM"
    private BigDecimal amount;
    private Long sourceAccountId;
    private Long destAccountId;
    private String errorMessage;      // Lý do lỗi (nullable)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DistributedTransactionLog() {
    }

    // Getters & Setters

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSourceBranch() { return sourceBranch; }
    public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }

    public String getDestBranch() { return destBranch; }
    public void setDestBranch(String destBranch) { this.destBranch = destBranch; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Long getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(Long sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public Long getDestAccountId() { return destAccountId; }
    public void setDestAccountId(Long destAccountId) { this.destAccountId = destAccountId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
