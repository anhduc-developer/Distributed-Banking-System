package dev.distributed.bank.service;

import dev.distributed.bank.distributed.SiteRouter;
import dev.distributed.bank.dto.request.CreateAccountRequest;
import dev.distributed.bank.dto.request.DepositRequest;
import dev.distributed.bank.dto.request.WithdrawRequest;
import dev.distributed.bank.dto.response.BalanceResponse;
import dev.distributed.bank.dto.response.ConcurrentWithdrawResponse;
import dev.distributed.bank.entity.Account;
import dev.distributed.bank.entity.TransactionHistory;
import dev.distributed.bank.exception.AccountInactiveException;
import dev.distributed.bank.exception.AccountNotFoundException;
import dev.distributed.bank.exception.InsufficientBalanceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service: Quản lý tài khoản + Giao dịch LOCAL (gửi/rút tiền).
 *
 * Tất cả giao dịch trong service này là LOCAL — chỉ ảnh hưởng 1 site.
 * Giao dịch phân tán (inter-branch) nằm ở TransferService.
 *
 * Sử dụng:
 * - JdbcTemplate: chạy SQL
 * - TransactionManager: quản lý BEGIN/COMMIT/ROLLBACK thủ công
 * - SELECT FOR UPDATE: pessimistic locking cho withdraw
 */
@Service
public class AccountService {

    private final SiteRouter siteRouter;

    /** RowMapper: chuyển ResultSet → Account object */
    private final RowMapper<Account> accountRowMapper = (rs, rowNum) -> {
        Account a = new Account();
        a.setAccountId(rs.getLong("account_id"));
        a.setCustomerId(rs.getLong("customer_id"));
        a.setBranchId(rs.getString("branch_id"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setStatus(rs.getString("status"));
        a.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return a;
    };

    /** RowMapper cho TransactionHistory */
    private final RowMapper<TransactionHistory> txnRowMapper = (rs, rowNum) -> {
        TransactionHistory t = new TransactionHistory();
        t.setTransactionId(rs.getLong("transaction_id"));
        t.setTransactionType(rs.getString("transaction_type"));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setAccountId(rs.getLong("account_id"));

        // Nullable fields
        long relatedAccId = rs.getLong("related_account_id");
        t.setRelatedAccountId(rs.wasNull() ? null : relatedAccId);
        t.setRelatedBranchId(rs.getString("related_branch_id"));
        t.setBalanceAfter(rs.getBigDecimal("balance_after"));
        t.setStatus(rs.getString("status"));
        t.setDistributedTxnId(rs.getString("distributed_txn_id"));
        t.setDescription(rs.getString("description"));
        t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return t;
    };

    public AccountService(SiteRouter siteRouter) {
        this.siteRouter = siteRouter;
    }

    // ============================================================
    // CRUD Operations
    // ============================================================

    /** Lấy danh sách tài khoản theo chi nhánh */
    public List<Account> getAccountsByBranch(String branchId) {
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
        return jdbc.query("SELECT * FROM account ORDER BY account_id", accountRowMapper);
    }

    /** Lấy chi tiết 1 tài khoản */
    public Account getAccountById(Long id, String branchId) {
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
        List<Account> accounts = jdbc.query(
                "SELECT * FROM account WHERE account_id = ?",
                accountRowMapper, id);
        if (accounts.isEmpty()) {
            throw new AccountNotFoundException(
                    "Account " + id + " not found at branch " + branchId);
        }
        return accounts.get(0);
    }

    /** Tạo tài khoản mới */
    public Account createAccount(CreateAccountRequest request) {
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(request.getBranchId());
        BigDecimal initialBalance = request.getInitialBalance() != null
                ? request.getInitialBalance()
                : BigDecimal.ZERO;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO account (customer_id, branch_id, balance, status) VALUES (?, ?, ?, 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, request.getCustomerId());
            ps.setString(2, request.getBranchId().toUpperCase());
            ps.setBigDecimal(3, initialBalance);
            return ps;
        }, keyHolder);

        Long newId = keyHolder.getKey().longValue();
        return getAccountById(newId, request.getBranchId());
    }

    /** Tra cứu số dư */
    public BalanceResponse getBalance(Long accountId, String branchId) {
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
        List<BalanceResponse> results = jdbc.query(
                "SELECT a.account_id, a.branch_id, a.balance, a.status, c.full_name " +
                        "FROM account a JOIN customer c ON a.customer_id = c.customer_id " +
                        "WHERE a.account_id = ?",
                (rs, rowNum) -> new BalanceResponse(
                        rs.getLong("account_id"),
                        rs.getString("branch_id"),
                        rs.getBigDecimal("balance"),
                        rs.getString("full_name"),
                        rs.getString("status")),
                accountId);
        if (results.isEmpty()) {
            throw new AccountNotFoundException(
                    "Account " + accountId + " not found at branch " + branchId);
        }
        return results.get(0);
    }

    // ============================================================
    // KIỂM TRA TRẠNG THÁI TÀI KHOẢN
    // ============================================================

    /**
     * Kiểm tra tài khoản có ACTIVE không.
     * Nếu không ACTIVE → throw AccountInactiveException.
     */
    private void checkAccountActive(JdbcTemplate jdbc, Long accountId) {
        String status = jdbc.queryForObject(
                "SELECT status FROM account WHERE account_id = ?",
                String.class, accountId);
        if (status == null) {
            throw new AccountNotFoundException("Account " + accountId + " not found");
        }
        if (!"ACTIVE".equals(status)) {
            throw new AccountInactiveException(
                    "Tài khoản " + accountId + " đang ở trạng thái " + status +
                            ". Chỉ tài khoản ACTIVE mới được phép giao dịch.");
        }
    }

    // ============================================================
    // GỬI TIỀN (Deposit) — Giao dịch LOCAL
    // ============================================================

    /**
     * Gửi tiền vào tài khoản.
     * Đây là giao dịch LOCAL — chỉ ảnh hưởng 1 site.
     *
     * Flow:
     * 1. Kiểm tra tài khoản ACTIVE
     * 2. BEGIN TRANSACTION
     * 3. UPDATE balance = balance + amount
     * 4. Ghi transaction_history
     * 5. COMMIT
     */
    public Account deposit(DepositRequest request) {

        // =========================
        // VALIDATE INPUT
        // =========================
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // =========================
        // LẤY THÔNG TIN
        // =========================
        String branchId = request.getBranchId();

        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);

        PlatformTransactionManager txManager = siteRouter.getTransactionManager(branchId);

        // =========================
        // KIỂM TRA TÀI KHOẢN ACTIVE
        // =========================
        checkAccountActive(jdbc, request.getAccountId());

        // =========================
        // BEGIN TRANSACTION
        // =========================
        TransactionStatus txStatus = txManager.getTransaction(new DefaultTransactionDefinition());

        try {

            System.out.println();
            System.out.println("══════════════════════════════════════");
            System.out.println("DEPOSIT TRANSACTION");
            System.out.println("══════════════════════════════════════");

            // =========================
            // 1. BALANCE BAN ĐẦU
            // =========================
            BigDecimal beforeBalance = jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ?",
                    BigDecimal.class,
                    request.getAccountId());

            System.out.println("BEFORE DEPOSIT");
            System.out.println("Balance = " + beforeBalance);

            // =========================
            // 2. UPDATE BALANCE
            // =========================
            jdbc.update(
                    "UPDATE account SET balance = balance + ? WHERE account_id = ?",
                    request.getAmount(),
                    request.getAccountId());

            // =========================
            // 3. BALANCE SAU UPDATE
            // =========================
            BigDecimal afterUpdateBalance = jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ?",
                    BigDecimal.class,
                    request.getAccountId());

            System.out.println();
            System.out.println("AFTER UPDATE (CHƯA COMMIT)");
            System.out.println("Deposit Amount = " + request.getAmount());
            System.out.println("Balance = " + afterUpdateBalance);

            // =========================
            // 4. GHI TRANSACTION HISTORY
            // =========================
            jdbc.update(
                    "INSERT INTO transaction_history "
                            + "(transaction_type, amount, account_id, balance_after, status, description) "
                            + "VALUES ('DEPOSIT', ?, ?, ?, 'SUCCESS', 'Nạp tiền')",
                    request.getAmount(),
                    request.getAccountId(),
                    afterUpdateBalance);

            System.out.println();
            System.out.println("TRANSACTION HISTORY INSERTED");

            // ==================================================
            // TEST SERVER CRASH
            // ==================================================
            // BẬT ĐOẠN NÀY ĐỂ TEST ROLLBACK
            // TẮT ĐI ĐỂ CHẠY BÌNH THƯỜNG
            // ==================================================

            /*
             * System.out.println();
             * System.out.println("💥 SERVER CRASH BEFORE COMMIT!");
             * 
             * Thread.sleep(3000);
             * 
             * throw new RuntimeException("SERVER CRASH");
             */

            // =========================
            // 5. COMMIT
            // =========================
            txManager.commit(txStatus);

            System.out.println();
            System.out.println("COMMIT SUCCESS");

            // =========================
            // 6. BALANCE SAU COMMIT
            // =========================
            BigDecimal finalBalance = jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ?",
                    BigDecimal.class,
                    request.getAccountId());

            System.out.println();
            System.out.println("AFTER COMMIT");
            System.out.println("Balance = " + finalBalance);

            System.out.println("══════════════════════════════════════");
            System.out.println();

            return getAccountById(request.getAccountId(), branchId);

        } catch (Exception e) {

            // =========================
            // ROLLBACK
            // =========================
            txManager.rollback(txStatus);

            System.out.println();
            System.out.println("ROLLBACK TRANSACTION");

            // =========================
            // BALANCE SAU ROLLBACK
            // =========================
            BigDecimal rollbackBalance = jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ?",
                    BigDecimal.class,
                    request.getAccountId());

            System.out.println();
            System.out.println("AFTER ROLLBACK");
            System.out.println("Balance = " + rollbackBalance);

            System.out.println("══════════════════════════════════════");
            System.out.println();

            throw new RuntimeException(e.getMessage());
        }
    }

    // ============================================================
    // RÚT TIỀN (Withdraw) — Giao dịch LOCAL + Pessimistic Lock
    // ============================================================

    /**
     * Rút tiền từ tài khoản (single thread).
     * Sử dụng SELECT FOR UPDATE (pessimistic locking) để tránh Lost Update.
     *
     * Flow:
     * 1. Kiểm tra tài khoản ACTIVE
     * 2. BEGIN TRANSACTION
     * 3. SELECT balance FROM account WHERE id=? FOR UPDATE ← LOCK row
     * 4. Kiểm tra balance >= amount
     * 5. UPDATE balance = balance - amount
     * 6. Ghi transaction_history
     * 7. COMMIT → release lock
     */
    public Account withdraw(WithdrawRequest request) {
        // Validate
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        String branchId = request.getBranchId();
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);

        // ⭐ Kiểm tra tài khoản ACTIVE trước khi giao dịch
        checkAccountActive(jdbc, request.getAccountId());

        PlatformTransactionManager txManager = siteRouter.getTransactionManager(branchId);
        TransactionStatus txStatus = txManager.getTransaction(new DefaultTransactionDefinition());

        try {
            // ⭐ PESSIMISTIC LOCK: SELECT FOR UPDATE
            // Lock row này → thread khác phải đợi đến khi COMMIT
            BigDecimal currentBalance = jdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                    BigDecimal.class, request.getAccountId());

            if (currentBalance == null) {
                throw new AccountNotFoundException(
                        "Account " + request.getAccountId() + " not found");
            }

            // Kiểm tra đủ tiền
            if (currentBalance.compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance. Current: " + currentBalance +
                                ", Requested: " + request.getAmount());
            }

            // Trừ tiền
            jdbc.update(
                    "UPDATE account SET balance = balance - ? WHERE account_id = ?",
                    request.getAmount(), request.getAccountId());

            BigDecimal newBalance = currentBalance.subtract(request.getAmount());

            // Ghi lịch sử
            jdbc.update(
                    "INSERT INTO transaction_history (transaction_type, amount, account_id, balance_after, status, description) "
                            +
                            "VALUES ('WITHDRAW', ?, ?, ?, 'SUCCESS', 'Rút tiền')",
                    request.getAmount(), request.getAccountId(), newBalance);

            txManager.commit(txStatus);

            System.out.println("✅ [WITHDRAW] Account " + request.getAccountId() +
                    " at " + branchId + ": -" + request.getAmount() +
                    " → Balance = " + newBalance);

            return getAccountById(request.getAccountId(), branchId);

        } catch (InsufficientBalanceException | AccountNotFoundException e) {
            txManager.rollback(txStatus);
            throw e;
        } catch (Exception e) {
            txManager.rollback(txStatus);
            throw new RuntimeException("Withdraw failed: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // RÚT TIỀN 2 THREAD — CÓ LOCK hoặc KHÔNG LOCK
    // ============================================================

    /**
     * Rút tiền bằng 2 thread đồng thời, mỗi thread rút số tiền RIÊNG do user nhập.
     *
     * useLock = true → SELECT FOR UPDATE (pessimistic lock)
     * → Thread 1 lock row, rút xong, COMMIT → Thread 2 mới được lock
     * → Thread 2 thấy balance đã giảm → nếu không đủ tiền → FAIL
     * → Kết quả ĐÚNG: không bị Lost Update
     *
     * useLock = false → SELECT bình thường (KHÔNG lock)
     * → Cả 2 thread đọc CÙNG balance → cả 2 thấy đủ tiền → cả 2 rút
     * → Kết quả SAI: balance bị âm (Lost Update)
     *
     * Luôn kiểm tra balance >= amount trước khi rút (nhưng không lock thì check bị
     * vô hiệu).
     */
    public ConcurrentWithdrawResponse concurrentWithdraw(WithdrawRequest request) {

        String branchId = request.getBranchId();
        Long accountId = request.getAccountId();

        BigDecimal amountThread1 = request.getAmountThread1();
        BigDecimal amountThread2 = request.getAmountThread2();

        boolean useLock = request.isUseLock();

        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);

        if (amountThread1 == null || amountThread1.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount Thread 1 phải > 0");
        }

        if (amountThread2 == null || amountThread2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount Thread 2 phải > 0");
        }

        checkAccountActive(jdbc, accountId);

        BigDecimal totalWithdraw = amountThread1.add(amountThread2);

        List<String> logs = Collections.synchronizedList(new ArrayList<>());

        ConcurrentWithdrawResponse response = new ConcurrentWithdrawResponse();

        BigDecimal initialBalance = jdbc.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?",
                BigDecimal.class,
                accountId);

        response.setInitialBalance(initialBalance);

        String mode;

        if (useLock) {
            mode = "WITH LOCK (SELECT FOR UPDATE)";
        } else {
            mode = "WITHOUT LOCK (Lost Update possible)";
        }

        System.out.println();
        System.out.println("══════════════════════════════════════════════");
        System.out.println("[CONCURRENT WITHDRAW] " + mode);
        System.out.println("Account: " + accountId);
        System.out.println("Branch : " + branchId);
        System.out.println("Initial Balance: " + initialBalance);
        System.out.println("T1 Withdraw: " + amountThread1);
        System.out.println("T2 Withdraw: " + amountThread2);
        System.out.println("Total Withdraw: " + totalWithdraw);
        System.out.println("══════════════════════════════════════════════");

        logs.add("[START] Concurrent Withdraw");
        logs.add("Mode = " + mode);
        logs.add("Initial Balance = " + initialBalance);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Cho 2 thread start cùng lúc
        CountDownLatch startLatch = new CountDownLatch(1);

        // Cho 2 thread đọc xong mới được write
        CyclicBarrier readBarrier = new CyclicBarrier(2);

        List<Future<String>> futures = new ArrayList<>();

        futures.add(executor.submit(() -> {

            logs.add("[T1] Ready");

            startLatch.await();

            if (useLock) {

                return withdrawWithLock(
                        branchId,
                        accountId,
                        amountThread1,
                        1,
                        logs);

            } else {

                return withdrawWithoutLock(
                        branchId,
                        accountId,
                        amountThread1,
                        1,
                        logs,
                        readBarrier);
            }
        }));

        futures.add(executor.submit(() -> {

            logs.add("[T2] Ready");

            startLatch.await();

            if (useLock) {

                return withdrawWithLock(
                        branchId,
                        accountId,
                        amountThread2,
                        2,
                        logs);

            } else {

                return withdrawWithoutLock(
                        branchId,
                        accountId,
                        amountThread2,
                        2,
                        logs,
                        readBarrier);
            }
        }));

        startLatch.countDown();

        String resultThread1 = "TIMEOUT";
        String resultThread2 = "TIMEOUT";

        try {
            resultThread1 = futures.get(0).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            logs.add("[T1] ERROR: " + e.getMessage());
        }

        try {
            resultThread2 = futures.get(1).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            logs.add("[T2] ERROR: " + e.getMessage());
        }

        executor.shutdown();

        // =========================
        // LẤY BALANCE CUỐI
        // =========================
        BigDecimal finalBalance = jdbc.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?",
                BigDecimal.class,
                accountId);

        // =========================
        // TÍNH BALANCE ĐÚNG
        // =========================
        BigDecimal expectedBalance = initialBalance;

        if (resultThread1.equals("SUCCESS")) {
            expectedBalance = expectedBalance.subtract(amountThread1);
        }

        if (resultThread2.equals("SUCCESS")) {
            expectedBalance = expectedBalance.subtract(amountThread2);
        }

        // =========================
        // KIỂM TRA LOST UPDATE
        // =========================
        boolean lostUpdate = false;

        if (finalBalance.compareTo(expectedBalance) != 0) {
            lostUpdate = true;
        }

        // =========================
        // LOG KẾT QUẢ
        // =========================
        logs.add("══════════════════════════════");
        logs.add("Expected Balance = " + expectedBalance);
        logs.add("Actual Balance   = " + finalBalance);

        if (lostUpdate) {

            logs.add("LOST UPDATE DETECTED!");

            BigDecimal diff = finalBalance.subtract(expectedBalance);

            logs.add("Difference = " + diff);

            logs.add("Một transaction đã bị ghi đè");

        } else {

            logs.add("Balance chính xác");
        }

        // =========================
        // IN CONSOLE
        // =========================
        System.out.println("──────────────────────────────");
        System.out.println("Expected Balance: " + expectedBalance);
        System.out.println("Actual Balance  : " + finalBalance);

        if (lostUpdate) {
            System.out.println("LOST UPDATE DETECTED!");
        } else {
            System.out.println("Balance chính xác");
        }

        System.out.println("══════════════════════════════════════════════");
        System.out.println();

        // =========================
        // BUILD RESPONSE
        // =========================
        response.setAccount(getAccountById(accountId, branchId));

        response.setFinalBalance(finalBalance);

        response.setExpectedBalance(expectedBalance);

        response.setLostUpdate(lostUpdate);

        response.setThread1Result(resultThread1);

        response.setThread2Result(resultThread2);

        response.setLogs(logs);

        return response;
    }

    /**
     * Rút tiền CÓ LOCK (SELECT FOR UPDATE) — trong 1 thread.
     * Thread phải đợi lock → đọc balance đúng → check đúng → kết quả đúng.
     */
    private String withdrawWithLock(String branchId, Long accountId,
            BigDecimal amount, int threadNum, List<String> logs) {
        String tag = "[T" + threadNum + "]";
        PlatformTransactionManager txManager = siteRouter.getTransactionManager(branchId);
        TransactionStatus txStatus = txManager.getTransaction(new DefaultTransactionDefinition());

        try {
            JdbcTemplate threadJdbc = siteRouter.getJdbcTemplate(branchId);

            logs.add(tag + " 🔒 Đang chờ lock (SELECT FOR UPDATE)...");
            System.out.println("   " + tag + " 🔒 Đang chờ lock...");

            // ⭐ PESSIMISTIC LOCK: SELECT FOR UPDATE
            BigDecimal balance = threadJdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                    BigDecimal.class, accountId);

            logs.add(tag + " ✅ Đã lock, balance = " + balance);
            System.out.println("   " + tag + " ✅ Acquired lock, balance = " + balance);

            // Kiểm tra số dư
            if (balance.compareTo(amount) < 0) {
                txManager.rollback(txStatus);
                String msg = tag + " ❌ FAILED: Không đủ tiền (" + balance + " < " + amount + ")";
                logs.add(msg);
                System.out.println("   " + msg);
                return "FAILED";
            }

            // Trừ tiền
            threadJdbc.update(
                    "UPDATE account SET balance = balance - ? WHERE account_id = ?",
                    amount, accountId);

            BigDecimal newBalance = balance.subtract(amount);

            // Ghi lịch sử
            threadJdbc.update(
                    "INSERT INTO transaction_history (transaction_type, amount, account_id, balance_after, status, description) "
                            +
                            "VALUES ('WITHDRAW', ?, ?, ?, 'SUCCESS', ?)",
                    amount, accountId, newBalance,
                    "Rút tiền concurrent WITH LOCK [T" + threadNum + "]");

            txManager.commit(txStatus);

            String msg = tag + " 💰 SUCCESS: rút " + amount + " → balance = " + newBalance + " — COMMITTED";
            logs.add(msg);
            System.out.println("   " + tag + " 💰 Rút " + amount +
                    " → balance = " + newBalance + " ✓ COMMITTED");

            return "SUCCESS";
        } catch (Exception e) {
            if (!txStatus.isCompleted())
                txManager.rollback(txStatus);
            String msg = tag + " ❌ ERROR: " + e.getMessage();
            logs.add(msg);
            System.out.println("   " + msg);
            return "ERROR";
        }
    }

    /**
     * Rút tiền KHÔNG LOCK — trong 1 thread.
     *
     * CẢ 2 THREAD ĐỌC BALANCE ĐỒNG THỜI → cùng thấy balance ban đầu →
     * cả 2 pass check balance >= amount → cả 2 UPDATE →
     * Thread sau ghi đè thread trước → Lost Update → balance SAI (bị âm).
     *
     * Dùng readBarrier để đảm bảo cả 2 thread ĐỌC XONG rồi mới WRITE.
     */
    private String withdrawWithoutLock(String branchId, Long accountId,
            BigDecimal amount, int threadNum,
            List<String> logs, CyclicBarrier readBarrier) {
        String tag = "[T" + threadNum + "]";

        try {
            JdbcTemplate threadJdbc = siteRouter.getJdbcTemplate(branchId);

            logs.add(tag + " ⚠️ Đọc balance KHÔNG LOCK...");
            System.out.println("   " + tag + " ⚠️ Đọc balance KHÔNG LOCK...");

            // ⚠️ KHÔNG CÓ FOR UPDATE → không lock → cả 2 thread đọc cùng lúc
            BigDecimal balance = threadJdbc.queryForObject(
                    "SELECT balance FROM account WHERE account_id = ?",
                    BigDecimal.class, accountId);

            logs.add(tag + " Đọc balance = " + balance + " (NO LOCK)");
            System.out.println("   " + tag + " 📖 Đọc balance = " + balance + " (NO LOCK)");

            // ⭐ ĐỢI THREAD KIA ĐỌC XONG → đảm bảo cả 2 đều đọc CÙNG giá trị
            // trước khi bất kỳ thread nào bắt đầu WRITE
            readBarrier.await(5, TimeUnit.SECONDS);

            // Kiểm tra số dư (CÓ check — nhưng cả 2 thread đọc cùng giá trị nên cả 2 đều
            // pass)
            if (balance.compareTo(amount) < 0) {
                String msg = tag + " ❌ FAILED: Không đủ tiền (" + balance + " < " + amount + ")";
                logs.add(msg);
                System.out.println("   " + msg);
                return "FAILED";
            }

            // ⚠️ LOST UPDATE PATTERN (Read → Compute in App → Write)
            // Cả 2 thread đọc CÙNG balance (ví dụ 1,000,000)
            // Mỗi thread tự tính: newBalance = balance_đọc_được - amount
            // T1: 1,000,000 - 600,000 = 400,000 → SET balance = 400,000
            // T2: 1,000,000 - 600,000 = 400,000 → SET balance = 400,000
            // Thread ghi SAU sẽ GHI ĐÈ thread ghi trước!
            // → Kết quả: balance = 400,000 (chỉ trừ 1 lần thay vì 2 lần)
            // → Expected: 1,000,000 - 600,000 - 600,000 = -200,000
            // → Actual: 400,000 → MỘT GIAO DỊCH BỊ MẤT!
            BigDecimal newBalance = balance.subtract(amount);

            threadJdbc.update(
                    "UPDATE account SET balance = ? WHERE account_id = ?",
                    newBalance, accountId);

            // Ghi lịch sử
            threadJdbc.update(
                    "INSERT INTO transaction_history (transaction_type, amount, account_id, balance_after, status, description) "
                            +
                            "VALUES ('WITHDRAW', ?, ?, ?, 'SUCCESS', ?)",
                    amount, accountId, newBalance,
                    "Rút tiền concurrent NO LOCK [T" + threadNum + "]");

            String msg = tag + " 💰 SUCCESS: rút " + amount +
                    " (đọc balance=" + balance + ", balance SAU UPDATE=" + newBalance + ") → COMMITTED";
            logs.add(msg);
            System.out.println("   " + tag + " 💰 Rút " + amount +
                    " (đọc balance=" + balance + " → balance sau UPDATE=" + newBalance + ") COMMITTED");

            return "SUCCESS";
        } catch (Exception e) {
            String msg = tag + " ❌ ERROR: " + e.getMessage();
            logs.add(msg);
            System.out.println("   " + msg);
            return "ERROR";
        }
    }

    // ============================================================
    // LỊCH SỬ GIAO DỊCH
    // ============================================================

    /** Lấy lịch sử giao dịch của 1 tài khoản */
    public List<TransactionHistory> getTransactionHistory(Long accountId, String branchId) {
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
        return jdbc.query(
                "SELECT * FROM transaction_history WHERE account_id = ? ORDER BY created_at DESC",
                txnRowMapper, accountId);
    }

    /** Đổi trạng thái tài khoản */
    public Account updateAccountStatus(Long accountId, String branchId, String status) {
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
        jdbc.update("UPDATE account SET status = ? WHERE account_id = ?", status, accountId);
        return getAccountById(accountId, branchId);
    }
}
