package dev.distributed.bank.entity;

import java.time.LocalDateTime;

/**
 * Entity: Participant trong 2-Phase Commit.
 * Mỗi giao dịch phân tán có 2 participants:
 *   - SOURCE: site bị trừ tiền (DEBIT)
 *   - DESTINATION: site được cộng tiền (CREDIT)
 *
 * Coordinator kiểm tra status của tất cả participant
 * trước khi quyết định COMMIT hay ABORT.
 */
public class TransactionParticipant {

    private Long id;              // PK
    private String txnId;         // FK → distributed_transaction_log
    private String branchId;      // Site tham gia: "HN", "DN", "HCM"
    private String role;          // "SOURCE" / "DESTINATION"
    private String status;        // PREPARING / PREPARED / COMMITTED / ABORTED / FAILED
    private String action;        // "DEBIT" / "CREDIT"
    private LocalDateTime createdAt;

    public TransactionParticipant() {
    }

    // Getters & Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
