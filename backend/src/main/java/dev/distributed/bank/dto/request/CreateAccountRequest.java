package dev.distributed.bank.dto.request;

import java.math.BigDecimal;

/**
 * DTO: Request tạo tài khoản mới.
 * Client gửi JSON body này khi gọi POST /api/accounts
 */
public class CreateAccountRequest {

    private Long customerId;           // Bắt buộc: khách hàng sở hữu
    private String branchId;           // Bắt buộc: chi nhánh quản lý
    private BigDecimal initialBalance; // Tuỳ chọn, default 0

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public BigDecimal getInitialBalance() { return initialBalance; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }
}
