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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
         * 
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

                // =========================
                // KIỂM TRA DỮ LIỆU ĐẦU VÀO
                // =========================
                if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Số tiền chuyển phải lớn hơn 0");
                }

                if (request.getFromAccountId().equals(request.getToAccountId())) {
                        throw new IllegalArgumentException("Không thể chuyển cho cùng một tài khoản");
                }

                String branchId = request.getBranchId();

                JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);

                PlatformTransactionManager txManager = siteRouter.getTransactionManager(branchId);

                TransactionStatus txStatus = txManager.getTransaction(new DefaultTransactionDefinition());

                try {

                        System.out.println("==========================================");
                        System.out.println("CHUYỂN TIỀN NỘI BỘ");
                        System.out.println("==========================================");

                        System.out.println("Chi nhánh      : " + branchId);

                        System.out.println("Tài khoản gửi  : " +
                                        request.getFromAccountId());

                        System.out.println("Tài khoản nhận : " +
                                        request.getToAccountId());

                        System.out.println("Số tiền chuyển : " +
                                        request.getAmount() + " VND");

                        // =========================
                        // LOCK 2 TÀI KHOẢN
                        // LOCK THEO THỨ TỰ ID ĐỂ TRÁNH DEADLOCK
                        // =========================
                        Long firstId = Math.min(
                                        request.getFromAccountId(),
                                        request.getToAccountId());

                        Long secondId = Math.max(
                                        request.getFromAccountId(),
                                        request.getToAccountId());

                        jdbc.queryForObject(
                                        "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                                        BigDecimal.class,
                                        firstId);

                        jdbc.queryForObject(
                                        "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                                        BigDecimal.class,
                                        secondId);

                        // =========================
                        // LẤY SỐ DƯ TÀI KHOẢN GỬI
                        // =========================
                        BigDecimal fromBalance = jdbc.queryForObject(
                                        "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                                        BigDecimal.class,
                                        request.getFromAccountId());

                        // =========================
                        // KIỂM TRA 2 TÀI KHOẢN ACTIVE
                        // =========================
                        checkAccountActive(
                                        jdbc,
                                        request.getFromAccountId(),
                                        "Source");

                        checkAccountActive(
                                        jdbc,
                                        request.getToAccountId(),
                                        "Dest");

                        // =========================
                        // KIỂM TRA ĐỦ TIỀN KHÔNG
                        // =========================
                        if (fromBalance.compareTo(request.getAmount()) < 0) {

                                throw new InsufficientBalanceException(
                                                "Không đủ số dư. Hiện tại: " +
                                                                fromBalance +
                                                                ", Yêu cầu: " +
                                                                request.getAmount());
                        }

                        // =========================
                        // TRỪ TIỀN TÀI KHOẢN GỬI
                        // =========================
                        jdbc.update(
                                        "UPDATE account SET balance = balance - ? WHERE account_id = ?",
                                        request.getAmount(),
                                        request.getFromAccountId());

                        BigDecimal newFromBalance = fromBalance.subtract(request.getAmount());

                        // =========================
                        // CỘNG TIỀN TÀI KHOẢN NHẬN
                        // =========================
                        jdbc.update(
                                        "UPDATE account SET balance = balance + ? WHERE account_id = ?",
                                        request.getAmount(),
                                        request.getToAccountId());

                        BigDecimal newToBalance = jdbc.queryForObject(
                                        "SELECT balance FROM account WHERE account_id = ?",
                                        BigDecimal.class,
                                        request.getToAccountId());

                        // =========================
                        // GHI LỊCH SỬ GIAO DỊCH
                        // =========================
                        jdbc.update(
                                        "INSERT INTO transaction_history " +
                                                        "(transaction_type, amount, account_id, related_account_id, balance_after, status, description) "
                                                        +
                                                        "VALUES ('TRANSFER_OUT', ?, ?, ?, ?, 'SUCCESS', 'Chuyển tiền nội bộ')",

                                        request.getAmount(),
                                        request.getFromAccountId(),
                                        request.getToAccountId(),
                                        newFromBalance);

                        jdbc.update(
                                        "INSERT INTO transaction_history " +
                                                        "(transaction_type, amount, account_id, related_account_id, balance_after, status, description) "
                                                        +
                                                        "VALUES ('TRANSFER_IN', ?, ?, ?, ?, 'SUCCESS', 'Nhận tiền nội bộ')",

                                        request.getAmount(),
                                        request.getToAccountId(),
                                        request.getFromAccountId(),
                                        newToBalance);

                        // =========================
                        // COMMIT TRANSACTION
                        // =========================
                        txManager.commit(txStatus);

                        System.out.println("------------------------------------------");

                        System.out.println("CHUYỂN TIỀN THÀNH CÔNG");

                        System.out.println("Số dư tài khoản gửi  : " +
                                        newFromBalance);

                        System.out.println("Số dư tài khoản nhận : " +
                                        newToBalance);

                        System.out.println("==========================================");

                        // =========================
                        // BUILD RESPONSE
                        // =========================
                        TransferResultResponse response = new TransferResultResponse();

                        response.setStatus("SUCCESS");

                        response.setFromBranch(branchId);

                        response.setFromAccountId(
                                        request.getFromAccountId());

                        response.setToBranch(branchId);

                        response.setToAccountId(
                                        request.getToAccountId());

                        response.setAmount(request.getAmount());

                        response.setSourceBalanceAfter(newFromBalance);

                        response.setDestBalanceAfter(newToBalance);

                        response.setMessage("Chuyển tiền nội bộ thành công");

                        return response;

                } catch (InsufficientBalanceException | AccountInactiveException e) {

                        txManager.rollback(txStatus);

                        throw e;

                } catch (Exception e) {

                        txManager.rollback(txStatus);

                        throw new RuntimeException(
                                        "Chuyển tiền nội bộ thất bại: " +
                                                        e.getMessage(),
                                        e);
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

                // =========================
                // KIỂM TRA DỮ LIỆU ĐẦU VÀO
                // =========================
                if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
                }

                if (request.getFromBranch().equalsIgnoreCase(request.getToBranch())) {
                        throw new IllegalArgumentException(
                                        "Cùng chi nhánh thì dùng internal transfer");
                }

                String txnId = "TXN-" + System.currentTimeMillis();

                String sourceBranch = request.getFromBranch().toUpperCase();

                String destBranch = request.getToBranch().toUpperCase();

                // =========================
                // TẠO LOGS CHO FRONTEND
                // =========================
                List<String> logs = Collections.synchronizedList(new ArrayList<>());

                System.out.println("==========================================");
                System.out.println("GIAO DỊCH 2PC LIÊN CHI NHÁNH");
                System.out.println("==========================================");

                System.out.println("Transaction ID : " + txnId);
                System.out.println("Từ chi nhánh   : " + sourceBranch);
                System.out.println("Đến chi nhánh  : " + destBranch);
                System.out.println("Số tiền        : " + request.getAmount());

                System.out.println("==========================================");

                logs.add("BẮT ĐẦU GIAO DỊCH 2PC");
                logs.add("Transaction ID: " + txnId);
                logs.add(sourceBranch + " → " + destBranch);
                logs.add("Số tiền: " + request.getAmount());

                TransferResultResponse response = new TransferResultResponse();

                // Lưu reference để dùng trong catch block
                BigDecimal[] balanceTracker = new BigDecimal[3];
                // [0] = sourceBalanceBefore
                // [1] = destBalanceBefore
                // [2] = sourceBalanceAfterDebit

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

                        logs.add("PHASE 1: PREPARE");

                        txnLogger.createTransactionLog(
                                        txnId,
                                        "INTER_BRANCH_TRANSFER",
                                        "PREPARING",
                                        sourceBranch,
                                        destBranch,
                                        request.getAmount(),
                                        request.getFromAccountId(),
                                        request.getToAccountId());

                        // =========================
                        // STEP 1: ĐỌC SỐ DƯ BAN ĐẦU
                        // =========================
                        logs.add("STEP 1: ĐỌC SỐ DƯ BAN ĐẦU");

                        // LOCK SOURCE
                        BigDecimal sourceBalance = sourceJdbc.queryForObject(
                                        "SELECT balance FROM account " +
                                                        "WHERE account_id = ? FOR UPDATE",
                                        BigDecimal.class,
                                        request.getFromAccountId());

                        if (sourceBalance == null) {

                                throw new AccountNotFoundException(
                                                "Không tìm thấy tài khoản nguồn");
                        }

                        checkAccountActive(
                                        sourceJdbc,
                                        request.getFromAccountId(),
                                        "Source (" + sourceBranch + ")");

                        // LOCK DEST
                        BigDecimal destBalance = destJdbc.queryForObject(
                                        "SELECT balance FROM account " +
                                                        "WHERE account_id = ? FOR UPDATE",
                                        BigDecimal.class,
                                        request.getToAccountId());

                        if (destBalance == null) {

                                throw new AccountNotFoundException(
                                                "Không tìm thấy tài khoản đích");
                        }

                        checkAccountActive(
                                        destJdbc,
                                        request.getToAccountId(),
                                        "Dest (" + destBranch + ")");

                        System.out.println();
                        System.out.println("STEP 1: SỐ DƯ BAN ĐẦU");

                        System.out.println(
                                        sourceBranch +
                                                        " - Account " +
                                                        request.getFromAccountId() +
                                                        ": " +
                                                        sourceBalance);

                        System.out.println(
                                        destBranch +
                                                        " - Account " +
                                                        request.getToAccountId() +
                                                        ": " +
                                                        destBalance);

                        logs.add(
                                        sourceBranch +
                                                        " balance = " +
                                                        sourceBalance);

                        logs.add(
                                        destBranch +
                                                        " balance = " +
                                                        destBalance);

                        // Lưu số dư ban đầu vào tracker
                        balanceTracker[0] = sourceBalance;
                        balanceTracker[1] = destBalance;

                        // =========================
                        // KIỂM TRA ĐỦ TIỀN KHÔNG
                        // =========================
                        if (sourceBalance.compareTo(request.getAmount()) < 0) {

                                throw new InsufficientBalanceException(
                                                "Không đủ số dư");
                        }

                        // =========================
                        // STEP 2: SOURCE TRỪ TIỀN
                        // =========================
                        logs.add("STEP 2: SOURCE TRỪ TIỀN");

                        BigDecimal sourceAfter = sourceBalance.subtract(request.getAmount());

                        sourceJdbc.update(
                                        "UPDATE account SET balance = ? " +
                                                        "WHERE account_id = ?",
                                        sourceAfter,
                                        request.getFromAccountId());

                        System.out.println();
                        System.out.println("STEP 2: SOURCE TRỪ TIỀN");

                        System.out.println(
                                        sourceBranch +
                                                        ": " +
                                                        sourceBalance +
                                                        " → " +
                                                        sourceAfter);

                        System.out.println(
                                        destBranch +
                                                        ": " +
                                                        destBalance +
                                                        " (chưa thay đổi)");

                        System.out.println(
                                        "PREPARE SOURCE THÀNH CÔNG");

                        logs.add(
                                        sourceBranch +
                                                        ": " +
                                                        sourceBalance +
                                                        " → " +
                                                        sourceAfter);

                        // Lưu số dư sau khi trừ
                        balanceTracker[2] = sourceAfter;

                        logs.add("PREPARE SOURCE THÀNH CÔNG");

                        // =========================
                        // GIẢ LẬP DESTINATION SERVER CRASH
                        // =========================
                        if (request.isSimulateCrash()) {

                                System.out.println();
                                System.out.println("DESTINATION SERVER BỊ CRASH");

                                System.out.println(
                                                destBranch +
                                                                " server bị crash");

                                System.out.println(
                                                "Source đã trừ tiền nhưng Dest chưa cộng");

                                logs.add("DESTINATION SERVER BỊ CRASH");

                                logs.add(
                                                sourceBranch +
                                                                " đã trừ tiền nhưng " +
                                                                destBranch +
                                                                " chưa cộng");

                                throw new RuntimeException(
                                                destBranch +
                                                                " server bị crash");
                        }

                        // =========================
                        // STEP 3: DEST CỘNG TIỀN
                        // =========================
                        logs.add("STEP 3: DEST CỘNG TIỀN");

                        BigDecimal destAfter = destBalance.add(request.getAmount());

                        destJdbc.update(
                                        "UPDATE account SET balance = ? " +
                                                        "WHERE account_id = ?",
                                        destAfter,
                                        request.getToAccountId());

                        System.out.println();
                        System.out.println("STEP 3: DEST CỘNG TIỀN");

                        System.out.println(
                                        sourceBranch +
                                                        ": " +
                                                        sourceAfter);

                        System.out.println(
                                        destBranch +
                                                        ": " +
                                                        destBalance +
                                                        " → " +
                                                        destAfter);

                        System.out.println(
                                        "PREPARE DEST THÀNH CÔNG");

                        logs.add(
                                        destBranch +
                                                        ": " +
                                                        destBalance +
                                                        " → " +
                                                        destAfter);

                        logs.add("PREPARE DEST THÀNH CÔNG");

                        System.out.println();
                        System.out.println(
                                        "TẤT CẢ SITE ĐÃ PREPARE THÀNH CÔNG");

                        System.out.println(
                                        "BẮT ĐẦU COMMIT");

                        logs.add("TẤT CẢ SITE ĐÃ PREPARE THÀNH CÔNG");
                        logs.add("PHASE 2: COMMIT");

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

                        System.out.println(
                                        "COMMIT SOURCE THÀNH CÔNG");

                        logs.add("COMMIT SOURCE THÀNH CÔNG");

                        // COMMIT DEST
                        destTxManager.commit(destTx);

                        System.out.println(
                                        "COMMIT DEST THÀNH CÔNG");

                        logs.add("COMMIT DEST THÀNH CÔNG");

                        txnLogger.updateTransactionStatus(
                                        txnId,
                                        sourceBranch,
                                        "COMMITTED",
                                        null);

                        // =========================
                        // STEP 4: SỐ DƯ SAU COMMIT
                        // =========================
                        logs.add("STEP 4: SỐ DƯ SAU COMMIT");

                        System.out.println();

                        System.out.println(
                                        "STEP 4: SỐ DƯ SAU COMMIT");

                        System.out.println(
                                        sourceBranch +
                                                        " - Account " +
                                                        request.getFromAccountId() +
                                                        ": " +
                                                        sourceAfter);

                        System.out.println(
                                        destBranch +
                                                        " - Account " +
                                                        request.getToAccountId() +
                                                        ": " +
                                                        destAfter);

                        logs.add(
                                        sourceBranch +
                                                        " balance cuối = " +
                                                        sourceAfter);

                        logs.add(
                                        destBranch +
                                                        " balance cuối = " +
                                                        destAfter);

                        System.out.println("==========================================");
                        System.out.println("TRANSACTION COMMITTED");
                        System.out.println("==========================================");

                        logs.add("TRANSACTION COMMITTED");

                        response.setStatus("SUCCESS");

                        response.setTransactionId(txnId);

                        response.setFromBranch(sourceBranch);

                        response.setToBranch(destBranch);

                        response.setFromAccountId(
                                        request.getFromAccountId());

                        response.setToAccountId(
                                        request.getToAccountId());

                        response.setAmount(
                                        request.getAmount());

                        response.setSourceBalanceBefore(balanceTracker[0]);
                        response.setDestBalanceBefore(balanceTracker[1]);
                        response.setSourceBalanceAfterDebit(balanceTracker[2]);

                        response.setSourceBalanceAfter(
                                        sourceAfter);

                        response.setDestBalanceAfter(
                                        destAfter);

                        response.setMessage(
                                        "Chuyển tiền thành công");

                        response.setLogs(logs);

                        return response;

                } catch (

                Exception e) {

                        System.out.println();

                        System.out.println(
                                        "LỖI: " + e.getMessage());

                        System.out.println(
                                        "ABORT TRANSACTION");

                        logs.add("LỖI: " + e.getMessage());

                        logs.add("ABORT TRANSACTION");

                        // =========================
                        // ROLLBACK SOURCE
                        // =========================
                        try {

                                if (!sourceTx.isCompleted()) {

                                        sourceTxManager.rollback(sourceTx);

                                        System.out.println(
                                                        "ROLLBACK SOURCE THÀNH CÔNG");

                                        logs.add(
                                                        "ROLLBACK SOURCE THÀNH CÔNG");
                                }

                        } catch (Exception ignored) {
                        }

                        // =========================
                        // ROLLBACK DEST
                        // =========================
                        try {

                                if (!destTx.isCompleted()) {

                                        destTxManager.rollback(destTx);

                                        System.out.println(
                                                        "ROLLBACK DEST THÀNH CÔNG");

                                        logs.add(
                                                        "ROLLBACK DEST THÀNH CÔNG");
                                }

                        } catch (Exception ignored) {
                        }

                        // =========================
                        // UPDATE TRANSACTION LOG
                        // =========================
                        try {

                                txnLogger.updateTransactionStatus(
                                                txnId,
                                                sourceBranch,
                                                "ABORTED",
                                                e.getMessage());

                        } catch (Exception ignored) {
                        }

                        // =========================
                        // SỐ DƯ SAU ROLLBACK
                        // =========================
                        logs.add("SỐ DƯ SAU ROLLBACK");

                        System.out.println();

                        System.out.println(
                                        "SỐ DƯ SAU ROLLBACK");

                        BigDecimal srcFinal = null;

                        BigDecimal dstFinal = null;

                        try {

                                srcFinal = sourceJdbc.queryForObject(
                                                "SELECT balance FROM account WHERE account_id = ?",
                                                BigDecimal.class,
                                                request.getFromAccountId());

                                System.out.println(
                                                sourceBranch +
                                                                " - Account " +
                                                                request.getFromAccountId() +
                                                                ": " +
                                                                srcFinal);

                                logs.add(
                                                sourceBranch +
                                                                " balance sau rollback = " +
                                                                srcFinal);

                        } catch (Exception ignored) {

                                logs.add(
                                                sourceBranch +
                                                                ": không thể đọc dữ liệu");
                        }

                        try {

                                dstFinal = destJdbc.queryForObject(
                                                "SELECT balance FROM account WHERE account_id = ?",
                                                BigDecimal.class,
                                                request.getToAccountId());

                                System.out.println(
                                                destBranch +
                                                                " - Account " +
                                                                request.getToAccountId() +
                                                                ": " +
                                                                dstFinal);

                                logs.add(
                                                destBranch +
                                                                " balance sau rollback = " +
                                                                dstFinal);

                        } catch (Exception ignored) {

                                logs.add(
                                                destBranch +
                                                                ": không thể đọc dữ liệu");
                        }

                        // =========================
                        // GHI LỊCH SỬ GIAO DỊCH THẤT BẠI
                        // (dùng transaction mới, độc lập với rollback)
                        // =========================
                        try {
                                sourceJdbc.update(
                                                "INSERT INTO transaction_history " +
                                                                "(transaction_type, amount, account_id, " +
                                                                "related_account_id, related_branch_id, " +
                                                                "balance_after, status, distributed_txn_id, description) " +
                                                                "VALUES ('INTER_BRANCH_OUT', ?, ?, ?, ?, ?, 'FAILED', ?, ?)",

                                                request.getAmount(),
                                                request.getFromAccountId(),
                                                request.getToAccountId(),
                                                destBranch,
                                                srcFinal,
                                                txnId,
                                                "Inter branch transfer FAILED - " + e.getMessage());

                                System.out.println("GHI LỊCH SỬ THẤT BẠI TẠI SOURCE THÀNH CÔNG");
                                logs.add("GHI LỊCH SỬ THẤT BẠI TẠI SOURCE");

                        } catch (Exception histEx) {
                                System.out.println("Không thể ghi lịch sử tại source: " + histEx.getMessage());
                        }

                        try {
                                destJdbc.update(
                                                "INSERT INTO transaction_history " +
                                                                "(transaction_type, amount, account_id, " +
                                                                "related_account_id, related_branch_id, " +
                                                                "balance_after, status, distributed_txn_id, description) " +
                                                                "VALUES ('INTER_BRANCH_IN', ?, ?, ?, ?, ?, 'FAILED', ?, ?)",

                                                request.getAmount(),
                                                request.getToAccountId(),
                                                request.getFromAccountId(),
                                                sourceBranch,
                                                dstFinal,
                                                txnId,
                                                "Inter branch transfer FAILED - " + e.getMessage());

                                System.out.println("GHI LỊCH SỬ THẤT BẠI TẠI DEST THÀNH CÔNG");
                                logs.add("GHI LỊCH SỬ THẤT BẠI TẠI DEST");

                        } catch (Exception histEx) {
                                System.out.println("Không thể ghi lịch sử tại dest: " + histEx.getMessage());
                        }

                        System.out.println("==========================================");
                        System.out.println("TRANSACTION ABORTED");
                        System.out.println("==========================================");

                        logs.add("TRANSACTION ABORTED");

                        // =========================
                        // BUILD FAILED RESPONSE
                        // =========================
                        response.setStatus("FAILED");

                        response.setTransactionId(txnId);

                        response.setFromBranch(sourceBranch);

                        response.setToBranch(destBranch);

                        response.setFromAccountId(
                                        request.getFromAccountId());

                        response.setToAccountId(
                                        request.getToAccountId());

                        response.setAmount(
                                        request.getAmount());

                        response.setSourceBalanceBefore(balanceTracker[0]);
                        response.setDestBalanceBefore(balanceTracker[1]);
                        response.setSourceBalanceAfterDebit(balanceTracker[2]);

                        response.setSourceBalanceAfter(srcFinal);

                        response.setDestBalanceAfter(dstFinal);

                        response.setMessage(e.getMessage());

                        response.setLogs(logs);

                        return response;
                }
        }
}
