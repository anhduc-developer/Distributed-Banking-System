package dev.distributed.bank.dto.request;

import java.math.BigDecimal;

/**
 * DTO: Request gửi tiền (nạp tiền vào tài khoản).
 * Client gửi JSON body này khi gọi POST /api/transactions/deposit
 */
public class DepositRequest {

    private Long accountId;     // Tài khoản cần nạp
    private String branchId;    // Chi nhánh của tài khoản
    private BigDecimal amount;  // Số tiền nạp (phải > 0)

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
