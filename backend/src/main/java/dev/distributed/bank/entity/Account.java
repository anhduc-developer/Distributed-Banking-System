package dev.distributed.bank.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity: Tài khoản ngân hàng.
 * Chứa balance — trường quan trọng nhất cho mọi giao dịch.
 *
 * LƯU Ý: balance dùng BigDecimal — KHÔNG BAO GIỜ dùng double cho tiền!
 * Lý do: double có lỗi floating-point (0.1 + 0.2 = 0.30000000000000004)
 */
public class Account {

    private Long accountId;       // PK, auto-increment
    private Long customerId;      // FK → customer
    private String branchId;      // FK → branch
    private BigDecimal balance;   // Số dư (VND) — PHẢI dùng BigDecimal
    private String status;        // "ACTIVE", "INACTIVE", "FROZEN"
    private LocalDateTime createdAt;

    public Account() {
    }

    public Account(Long customerId, String branchId, BigDecimal balance) {
        this.customerId = customerId;
        this.branchId = branchId;
        this.balance = balance;
        this.status = "ACTIVE";
    }

    // Getters & Setters

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
