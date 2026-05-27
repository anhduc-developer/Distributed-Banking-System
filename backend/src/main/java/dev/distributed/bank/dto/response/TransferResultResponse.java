package dev.distributed.bank.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO: Response kết quả chuyển tiền.
 * Trả về sau khi thực hiện transfer (cả internal và inter-branch).
 */
public class TransferResultResponse {

    private String transactionId; // UUID giao dịch phân tán (nếu inter-branch)
    private String status; // "SUCCESS" / "FAILED" / "ROLLED_BACK"
    private String fromBranch;
    private Long fromAccountId;
    private String toBranch;
    private Long toAccountId;
    private BigDecimal amount;
    private BigDecimal sourceBalanceBefore;     // Số dư source ban đầu
    private BigDecimal destBalanceBefore;       // Số dư dest ban đầu
    private BigDecimal sourceBalanceAfterDebit; // Số dư source sau khi trừ (trước rollback)
    private BigDecimal sourceBalanceAfter;      // Số dư source sau giao dịch (hoặc sau rollback)
    private BigDecimal destBalanceAfter;        // Số dư dest sau giao dịch (hoặc sau rollback)
    private String message;
    private LocalDateTime timestamp;
    private List<String> logs;

    public TransferResultResponse() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters & Setters

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFromBranch() {
        return fromBranch;
    }

    public void setFromBranch(String fromBranch) {
        this.fromBranch = fromBranch;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(Long fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public String getToBranch() {
        return toBranch;
    }

    public void setToBranch(String toBranch) {
        this.toBranch = toBranch;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(Long toAccountId) {
        this.toAccountId = toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getSourceBalanceBefore() {
        return sourceBalanceBefore;
    }

    public void setSourceBalanceBefore(BigDecimal sourceBalanceBefore) {
        this.sourceBalanceBefore = sourceBalanceBefore;
    }

    public BigDecimal getDestBalanceBefore() {
        return destBalanceBefore;
    }

    public void setDestBalanceBefore(BigDecimal destBalanceBefore) {
        this.destBalanceBefore = destBalanceBefore;
    }

    public BigDecimal getSourceBalanceAfterDebit() {
        return sourceBalanceAfterDebit;
    }

    public void setSourceBalanceAfterDebit(BigDecimal sourceBalanceAfterDebit) {
        this.sourceBalanceAfterDebit = sourceBalanceAfterDebit;
    }

    public BigDecimal getSourceBalanceAfter() {
        return sourceBalanceAfter;
    }

    public void setSourceBalanceAfter(BigDecimal sourceBalanceAfter) {
        this.sourceBalanceAfter = sourceBalanceAfter;
    }

    public BigDecimal getDestBalanceAfter() {
        return destBalanceAfter;
    }

    public void setDestBalanceAfter(BigDecimal destBalanceAfter) {
        this.destBalanceAfter = destBalanceAfter;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }
}
