package dev.distributed.bank.dto.request;

import java.math.BigDecimal;

/**
 * DTO: Request chuyển tiền cùng chi nhánh (Internal Transfer).
 * Cả 2 tài khoản đều thuộc cùng 1 site → giao dịch LOCAL, không cần 2PC.
 * Client gửi JSON body này khi gọi POST /api/transfers/internal
 */
public class InternalTransferRequest {

    private Long fromAccountId;    // Tài khoản nguồn
    private Long toAccountId;      // Tài khoản đích
    private String branchId;       // Chi nhánh (cả 2 cùng chi nhánh)
    private BigDecimal amount;     // Số tiền chuyển

    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }

    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
