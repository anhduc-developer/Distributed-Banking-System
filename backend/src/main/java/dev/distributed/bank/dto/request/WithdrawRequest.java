package dev.distributed.bank.dto.request;

import java.math.BigDecimal;

/**
 * DTO: Request rút tiền.
 * Client gửi JSON body này khi gọi POST /api/transactions/withdraw
 * hoặc POST /api/transactions/concurrent-withdraw
 */
public class WithdrawRequest {

    private Long accountId;           // Tài khoản cần rút
    private String branchId;          // Chi nhánh của tài khoản
    private BigDecimal amount;        // Số tiền rút (single thread)
    private BigDecimal amountThread1; // Số tiền Thread 1 rút (concurrent mode)
    private BigDecimal amountThread2; // Số tiền Thread 2 rút (concurrent mode)
    private boolean useLock = true;   // true = SELECT FOR UPDATE, false = no lock

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getAmountThread1() { return amountThread1; }
    public void setAmountThread1(BigDecimal amountThread1) { this.amountThread1 = amountThread1; }

    public BigDecimal getAmountThread2() { return amountThread2; }
    public void setAmountThread2(BigDecimal amountThread2) { this.amountThread2 = amountThread2; }

    public boolean isUseLock() { return useLock; }
    public void setUseLock(boolean useLock) { this.useLock = useLock; }
}
