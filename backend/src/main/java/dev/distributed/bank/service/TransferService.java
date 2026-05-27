package dev.distributed.bank.service;

import dev.distributed.bank.distributed.SiteRouter;
import dev.distributed.bank.distributed.TransactionLogger;
import dev.distributed.bank.dto.request.InterBranchTransferRequest;
import dev.distributed.bank.dto.request.InternalTransferRequest;
import dev.distributed.bank.dto.response.TransferResultResponse;
import dev.distributed.bank.exception.AccountInactiveException;
import dev.distributed.bank.exception.AccountNotFoundException;
import dev.distributed.bank.exception.InsufficientBalanceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service: Chuyển tiền — bao gồm LOCAL transfer và DISTRIBUTED transfer (2PC).
 *
 * ĐÂY LÀ SERVICE QUAN TRỌNG NHẤT CỦA ĐỒ ÁN!
 *
 * 2 loại chuyển tiền:
 * 1. Internal Transfer: cùng chi nhánh → 1 site → 1 database transaction
 * 2. Inter-Branch Transfer: khác chi nhánh → 2 site → 2-Phase Commit
 *
 * Inter-Branch Transfer Flow (2PC):
 * PHASE 1 (PREPARE):
 * - Source site: lock account + debit (trừ tiền)
 * - Dest site: lock account + credit (cộng tiền)
 * PHASE 2 (COMMIT / ABORT):
 * - Nếu cả 2 thành công → COMMIT cả 2
 * - Nếu bất kỳ lỗi → ROLLBACK (compensation)
 */
@Service
public class TransferService {

    private final SiteRouter siteRouter;
    private final TransactionLogger txnLogger;

    public TransferService(SiteRouter siteRouter, TransactionLogger txnLogger) {
        this.siteRouter = siteRouter;
        this.txnLogger = txnLogger;
    }

    // ============================================================
    // KIỂM TRA TÀI KHOẢN ACTIVE
    // ============================================================

    /**
     * Kiểm tra tài khoản có ACTIVE không.
     * @param label nhãn (Source / Dest) để in log rõ ràng
     */
    private void checkAccountActive(JdbcTemplate jdbc, Long accountId, String label) {
        String status = jdbc.queryForObject(
                "SELECT status FROM account WHERE account_id = ?",
                String.class, accountId);
        if (status == null) {
            throw new AccountNotFoundException(
                    label + " account " + accountId + " not found");
        }
        if (!"ACTIVE".equals(status)) {
            throw new AccountInactiveException(
                    label + " tài khoản " + accountId + " đang ở trạng thái " + status +
                    ". Chỉ tài khoản ACTIVE mới được phép chuyển tiền.");
        }
    }

    // ============================================================
    // CHUYỂN TIỀN CÙNG CHI NHÁNH (Internal Transfer)
    // Giao dịch LOCAL — chỉ 1 site, 1 database transaction
    // ============================================================

    public TransferResultResponse internalTransfer(InternalTransferRequest request) {
        // Validate
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        String branchId = request.getBranchId();
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
        PlatformTransactionManager txManager = siteRouter.getTransactionManager(branchId);

        TransactionStatus txStatus = txManager.getTransaction(new DefaultTransactionDefinition());

        try {
            System.out.println("═══════════════════════════════════════════");
            System.out.println("[INTERNAL TRANSFER] " + branchId +
                    ": Account " + request.getFromAccountId() + " → Account " + request.getToAccountId());
            System.out.println("[AMOUNT] " + request.getAmount() + " VND");

            // Lock cả 2 account (lock theo thứ tự ID nhỏ trước để tránh deadlock)
            Long firstId = Math.min(request.getFromAccountId(), request.getToAccountId());
            Long secondId = Math.max(request.getFromAccountId(), request.getToAccountId());

            jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                    BigDecimal.class, firstId);
            BigDecimal fromBalance = jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                    BigDecimal.class, secondId);

            // Lấy balance source (có thể là firstId hoặc secondId)
            fromBalance = jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                    BigDecimal.class, request.getFromAccountId());

            // ⭐ Kiểm tra cả 2 tài khoản ACTIVE
            checkAccountActive(jdbc, request.getFromAccountId(), "Source");
            checkAccountActive(jdbc, request.getToAccountId(), "Dest");

            // Kiểm tra đủ tiền
            if (fromBalance.compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance. Current: " + fromBalance +
                                ", Requested: " + request.getAmount());
            }

            // Trừ tiền source
            jdbc.update("UPDATE account SET balance = balance - ? WHERE account_id = ?",
                    request.getAmount(), request.getFromAccountId());
            BigDecimal newFromBalance = fromBalance.subtract(request.getAmount());

            // Cộng tiền dest
            jdbc.update("UPDATE account SET balance = balance + ? WHERE account_id = ?",
                    request.getAmount(), request.getToAccountId());
            BigDecimal newToBalance = jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ?",
                    BigDecimal.class, request.getToAccountId());

            // Ghi lịch sử cho cả 2 tài khoản
            jdbc.update(
                    "INSERT INTO transaction_history (transaction_type, amount, account_id, related_account_id, balance_after, status, description) "
                            +
                            "VALUES ('TRANSFER_OUT', ?, ?, ?, ?, 'SUCCESS', 'Chuyển tiền nội bộ')",
                    request.getAmount(), request.getFromAccountId(), request.getToAccountId(), newFromBalance);

            jdbc.update(
                    "INSERT INTO transaction_history (transaction_type, amount, account_id, related_account_id, balance_after, status, description) "
                            +
                            "VALUES ('TRANSFER_IN', ?, ?, ?, ?, 'SUCCESS', 'Nhận tiền nội bộ')",
                    request.getAmount(), request.getToAccountId(), request.getFromAccountId(), newToBalance);

            txManager.commit(txStatus);

            System.out.println("[RESULT] SUCCESS — Source: " + newFromBalance + ", Dest: " + newToBalance);
            System.out.println("═══════════════════════════════════════════");

            // Build response
            TransferResultResponse response = new TransferResultResponse();
            response.setStatus("SUCCESS");
            response.setFromBranch(branchId);
            response.setFromAccountId(request.getFromAccountId());
            response.setToBranch(branchId);
            response.setToAccountId(request.getToAccountId());
            response.setAmount(request.getAmount());
            response.setSourceBalanceAfter(newFromBalance);
            response.setDestBalanceAfter(newToBalance);
            response.setMessage("Internal transfer successful");
            return response;

        } catch (InsufficientBalanceException | AccountInactiveException e) {
            txManager.rollback(txStatus);
            throw e;
        } catch (Exception e) {
            txManager.rollback(txStatus);
            throw new RuntimeException("Internal transfer failed: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // CHUYỂN TIỀN KHÁC CHI NHÁNH (Inter-Branch Transfer)
    // ⭐ GIAO DỊCH PHÂN TÁN — 2-PHASE COMMIT ⭐
    // ============================================================

    /**
     * Chuyển tiền liên chi nhánh sử dụng 2-Phase Commit.
     *
     * PHASE 1 — PREPARE:
     * Bước 1: Source site — Lock account + debit (trừ tiền)
     * Bước 2: Dest site — Lock account + credit (cộng tiền)
     *
     * PHASE 2 — DECISION:
     * Nếu cả 2 bước thành công → COMMIT cả 2 transaction
     * Nếu bất kỳ bước nào lỗi → ROLLBACK (compensation)
     */
    public TransferResultResponse interBranchTransfer(
            InterBranchTransferRequest request) {

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (request.getFromBranch().equalsIgnoreCase(request.getToBranch())) {
            throw new IllegalArgumentException(
                    "Use internal transfer for same branch");
        }

        String txnId = "TXN-" + System.currentTimeMillis();

        String sourceBranch = request.getFromBranch().toUpperCase();

        String destBranch = request.getToBranch().toUpperCase();

        System.out.println("═══════════════════════════════════════════");
        System.out.println("[2PC] Transaction: " + txnId);
        System.out.println("[2PC] " + sourceBranch + " → " + destBranch);
        System.out.println("[2PC] Amount: " + request.getAmount());
        System.out.println("═══════════════════════════════════════════");

        TransferResultResponse response = new TransferResultResponse();

        response.setTransactionId(txnId);

        JdbcTemplate sourceJdbc = siteRouter.getJdbcTemplate(sourceBranch);

        JdbcTemplate destJdbc = siteRouter.getJdbcTemplate(destBranch);

        PlatformTransactionManager sourceTxManager = siteRouter.getTransactionManager(sourceBranch);

        PlatformTransactionManager destTxManager = siteRouter.getTransactionManager(destBranch);

        TransactionStatus sourceTx = sourceTxManager.getTransaction(
                new DefaultTransactionDefinition());

        TransactionStatus destTx = destTxManager.getTransaction(
                new DefaultTransactionDefinition());

        try {

            // =====================================================
            // PHASE 1 — PREPARE
            // =====================================================

            txnLogger.createTransactionLog(
                    txnId,
                    "INTER_BRANCH_TRANSFER",
                    "PREPARING",
                    sourceBranch,
                    destBranch,
                    request.getAmount(),
                    request.getFromAccountId(),
                    request.getToAccountId());

            // ───────────────────────────────────────────
            // STEP 1: Đọc số dư ban đầu cả 2 tài khoản
            // ───────────────────────────────────────────

            // SOURCE LOCK
            BigDecimal sourceBalance = sourceJdbc.queryForObject(
                    "SELECT balance FROM account " +
                            "WHERE account_id = ? FOR UPDATE",
                    BigDecimal.class,
                    request.getFromAccountId());

            if (sourceBalance == null) {
                throw new AccountNotFoundException(
                        "Source account not found");
            }

            checkAccountActive(sourceJdbc, request.getFromAccountId(), "Source (" + sourceBranch + ")");

            // DEST LOCK
            BigDecimal destBalance = destJdbc.queryForObject(
                    "SELECT balance FROM account " +
                            "WHERE account_id = ? FOR UPDATE",
                    BigDecimal.class,
                    request.getToAccountId());

            if (destBalance == null) {
                throw new AccountNotFoundException(
                        "Destination account not found");
            }

            checkAccountActive(destJdbc, request.getToAccountId(), "Dest (" + destBranch + ")");

            System.out.println();
            System.out.println("── STEP 1: SỐ DƯ BAN ĐẦU ──");
            System.out.println("   " + sourceBranch + " (Account " + request.getFromAccountId() + "): " + sourceBalance);
            System.out.println("   " + destBranch + " (Account " + request.getToAccountId() + "): " + destBalance);

            if (sourceBalance.compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance");
            }

            // ───────────────────────────────────────────
            // STEP 2: Source trừ tiền (PREPARE SOURCE)
            // ───────────────────────────────────────────
            BigDecimal sourceAfter = sourceBalance.subtract(request.getAmount());

            sourceJdbc.update(
                    "UPDATE account SET balance = ? " +
                            "WHERE account_id = ?",
                    sourceAfter,
                    request.getFromAccountId());

            System.out.println();
            System.out.println("── STEP 2: SOURCE TRỪ TIỀN (" + sourceBranch + ") ──");
            System.out.println("   " + sourceBranch + ": " + sourceBalance + " → " + sourceAfter + " (trừ " + request.getAmount() + ")");
            System.out.println("   " + destBranch + ": " + destBalance + " (chưa thay đổi)");
            System.out.println("   [PREPARE] Source " + sourceBranch + " OK");

            // ───────────────────────────────────────────
            // 💥 SIMULATE CRASH nếu được bật
            // ───────────────────────────────────────────
            if (request.isSimulateCrash()) {
                System.out.println();
                System.out.println("── 💥 DESTINATION SERVER CRASH! ──");
                System.out.println("   " + destBranch + " server bị crash!");
                System.out.println("   Source " + sourceBranch + " đã trừ tiền nhưng Dest chưa cộng!");

                throw new RuntimeException(
                        "💥 " + destBranch + " server CRASH! Destination không thể cộng tiền.");
            }

            // ───────────────────────────────────────────
            // STEP 3: Dest cộng tiền (PREPARE DEST)
            // ───────────────────────────────────────────
            BigDecimal destAfter = destBalance.add(request.getAmount());

            destJdbc.update(
                    "UPDATE account SET balance = ? " +
                            "WHERE account_id = ?",
                    destAfter,
                    request.getToAccountId());

            System.out.println();
            System.out.println("── STEP 3: DEST CỘNG TIỀN (" + destBranch + ") ──");
            System.out.println("   " + sourceBranch + ": " + sourceAfter);
            System.out.println("   " + destBranch + ": " + destBalance + " → " + destAfter + " (cộng " + request.getAmount() + ")");
            System.out.println("   [PREPARE] Dest " + destBranch + " OK");

            System.out.println();
            System.out.println("── [DECISION] ALL SITES PREPARED → COMMIT ──");

            // =====================================================
            // PHASE 2 — COMMIT
            // =====================================================

            // HISTORY SOURCE
            sourceJdbc.update(
                    "INSERT INTO transaction_history " +
                            "(transaction_type, amount, account_id, " +
                            "related_account_id, related_branch_id, " +
                            "balance_after, status, distributed_txn_id, description) " +
                            "VALUES ('INTER_BRANCH_OUT', ?, ?, ?, ?, ?, 'SUCCESS', ?, ?)",

                    request.getAmount(),
                    request.getFromAccountId(),
                    request.getToAccountId(),
                    destBranch,
                    sourceAfter,
                    txnId,
                    "Inter branch transfer out");

            // HISTORY DEST
            destJdbc.update(
                    "INSERT INTO transaction_history " +
                            "(transaction_type, amount, account_id, " +
                            "related_account_id, related_branch_id, " +
                            "balance_after, status, distributed_txn_id, description) " +
                            "VALUES ('INTER_BRANCH_IN', ?, ?, ?, ?, ?, 'SUCCESS', ?, ?)",

                    request.getAmount(),
                    request.getToAccountId(),
                    request.getFromAccountId(),
                    sourceBranch,
                    destAfter,
                    txnId,
                    "Inter branch transfer in");

            // COMMIT SOURCE
            sourceTxManager.commit(sourceTx);

            System.out.println("   [COMMIT] Source " + sourceBranch + " SUCCESS");

            // COMMIT DEST
            destTxManager.commit(destTx);

            System.out.println("   [COMMIT] Dest " + destBranch + " SUCCESS");

            txnLogger.updateTransactionStatus(
                    txnId,
                    sourceBranch,
                    "COMMITTED",
                    null);

            // ───────────────────────────────────────────
            // STEP 4: SỐ DƯ SAU COMMIT
            // ───────────────────────────────────────────
            System.out.println();
            System.out.println("── STEP 4: SỐ DƯ SAU COMMIT ──");
            System.out.println("   " + sourceBranch + " (Account " + request.getFromAccountId() + "): " + sourceAfter);
            System.out.println("   " + destBranch + " (Account " + request.getToAccountId() + "): " + destAfter);
            System.out.println("═══════════════════════════════════════════");
            System.out.println("[RESULT] TRANSACTION COMMITTED ✅");
            System.out.println("═══════════════════════════════════════════");
            System.out.println();

            response.setStatus("SUCCESS");

            response.setTransactionId(txnId);

            response.setFromBranch(sourceBranch);
            response.setToBranch(destBranch);

            response.setFromAccountId(request.getFromAccountId());
            response.setToAccountId(request.getToAccountId());

            response.setAmount(request.getAmount());

            response.setSourceBalanceAfter(sourceAfter);
            response.setDestBalanceAfter(destAfter);

            response.setMessage("Transfer successful");

            return response;

        } catch (Exception e) {

            System.out.println();
            System.out.println("── ❌ ERROR: " + e.getMessage() + " ──");
            System.out.println("── [DECISION] ABORT TRANSACTION ──");

            // ───────────────────────────────────────────
            // ROLLBACK CẢ 2 SITE
            // ───────────────────────────────────────────

            try {
                if (!sourceTx.isCompleted()) {
                    sourceTxManager.rollback(sourceTx);
                    System.out.println("   [ROLLBACK] Source " + sourceBranch + " → ROLLBACK SUCCESS");
                }
            } catch (Exception ignored) {
            }

            try {
                if (!destTx.isCompleted()) {
                    destTxManager.rollback(destTx);
                    System.out.println("   [ROLLBACK] Dest " + destBranch + " → ROLLBACK SUCCESS");
                }
            } catch (Exception ignored) {
            }

            try {
                txnLogger.updateTransactionStatus(
                        txnId,
                        sourceBranch,
                        "ABORTED",
                        e.getMessage());
            } catch (Exception ignored) {
            }

            // ───────────────────────────────────────────
            // SỐ DƯ SAU ROLLBACK
            // ───────────────────────────────────────────
            System.out.println();
            System.out.println("── SỐ DƯ SAU ROLLBACK ──");
            try {
                BigDecimal srcFinal = sourceJdbc.queryForObject(
                        "SELECT balance FROM account WHERE account_id = ?",
                        BigDecimal.class, request.getFromAccountId());
                System.out.println("   " + sourceBranch + " (Account " + request.getFromAccountId() + "): " + srcFinal + " ← HOÀN TIỀN!");
            } catch (Exception ignored) {
                System.out.println("   " + sourceBranch + ": không thể đọc");
            }
            try {
                BigDecimal dstFinal = destJdbc.queryForObject(
                        "SELECT balance FROM account WHERE account_id = ?",
                        BigDecimal.class, request.getToAccountId());
                System.out.println("   " + destBranch + " (Account " + request.getToAccountId() + "): " + dstFinal + " ← KHÔNG ĐỔI");
            } catch (Exception ignored) {
                System.out.println("   " + destBranch + ": không thể đọc (server crashed)");
            }

            System.out.println("═══════════════════════════════════════════");
            System.out.println("[RESULT] TRANSACTION ABORTED ❌");
            System.out.println("═══════════════════════════════════════════");
            System.out.println();

            response.setStatus("FAILED");
            response.setMessage(e.getMessage());

            return response;
        }
    }
}
