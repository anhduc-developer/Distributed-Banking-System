package dev.distributed.bank.dto.request;

import java.math.BigDecimal;

/**
 * DTO: Request chuyển tiền KHÁC chi nhánh (Inter-Branch Transfer).
 * 2 tài khoản thuộc 2 site khác nhau → cần giao dịch PHÂN TÁN + 2PC.
 * Client gửi JSON body này khi gọi POST /api/transfers/inter-branch
 *
 * Đây là API quan trọng nhất cho đồ án CSDL phân tán!
 */
public class InterBranchTransferRequest {

    private String fromBranch;     // Chi nhánh nguồn: "HN"
    private Long fromAccountId;    // Tài khoản nguồn tại chi nhánh nguồn
    private String toBranch;       // Chi nhánh đích: "HCM"
    private Long toAccountId;      // Tài khoản đích tại chi nhánh đích
    private BigDecimal amount;     // Số tiền chuyển
    private boolean simulateCrash; // true → giả lập dest server crash

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

    public boolean isSimulateCrash() { return simulateCrash; }
    public void setSimulateCrash(boolean simulateCrash) { this.simulateCrash = simulateCrash; }
}
