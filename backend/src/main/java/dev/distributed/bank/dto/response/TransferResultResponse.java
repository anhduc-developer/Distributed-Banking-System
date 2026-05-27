package dev.distributed.bank.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO: Response kết quả chuyển tiền.
 * Trả về sau khi thực hiện transfer (cả internal và inter-branch).
 */
public class TransferResultResponse {

    private String transactionId;          // UUID giao dịch phân tán (nếu inter-branch)
    private String status;                 // "SUCCESS" / "FAILED" / "ROLLED_BACK"
    private String fromBranch;
    private Long fromAccountId;
    private String toBranch;
    private Long toAccountId;
    private BigDecimal amount;
    private BigDecimal sourceBalanceAfter;  // Số dư source sau giao dịch
    private BigDecimal destBalanceAfter;    // Số dư dest sau giao dịch
    private String message;
    private LocalDateTime timestamp;

    public TransferResultResponse() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters & Setters

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFromBranch() { return fromBranch; }
    public void setFromBranch(String fromBranch) { this.fromBranch = fromBranch; }

    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }

    public String getToBranch() { return toBranch; }
    public void setToBranch(String toBranch) { this.toBranch = toBranch; }

    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getSourceBalanceAfter() { return sourceBalanceAfter; }
    public void setSourceBalanceAfter(BigDecimal sourceBalanceAfter) { this.sourceBalanceAfter = sourceBalanceAfter; }

    public BigDecimal getDestBalanceAfter() { return destBalanceAfter; }
    public void setDestBalanceAfter(BigDecimal destBalanceAfter) { this.destBalanceAfter = destBalanceAfter; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
