package dev.distributed.bank.service;

import dev.distributed.bank.distributed.SiteRouter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service: Demo — Mô phỏng lỗi + demo concurrency control.
 *
 * Cung cấp các endpoint đặc biệt cho demo:
 * 1. Simulate site down — bật/tắt flag mô phỏng site lỗi
 * 2. Concurrent withdraw WITH lock — demo pessimistic locking hoạt động đúng
 * 3. Concurrent withdraw WITHOUT lock — demo Lost Update (sai)
 */
@Service
public class DemoService {

    private final SiteRouter siteRouter;

    public DemoService(SiteRouter siteRouter) {
        this.siteRouter = siteRouter;
    }

    // ============================================================
    // MÔ PHỎNG SITE DOWN
    // ============================================================

    public Map<String, Object> simulateSiteDown(String branchId, boolean enabled) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (enabled) {
            siteRouter.simulateSiteDown(branchId);
            result.put("status", "Site " + branchId + " is now DOWN (simulated)");
        } else {
            siteRouter.clearSiteDown();
            result.put("status", "All sites are now UP");
        }
        result.put("simulatedDownSite", siteRouter.getSimulatedDownSite());
        return result;
    }

    // ============================================================
    // DEMO CONCURRENT WITHDRAW — WITH PESSIMISTIC LOCK
    // ============================================================

    /**
     * Demo 2 thread cùng rút tiền từ 1 tài khoản (CÓ lock).
     * Kết quả ĐÚNG: balance cuối = balance đầu - (amount × 2)
     *
     * Mỗi thread dùng SELECT FOR UPDATE → thread sau phải đợi thread trước COMMIT.
     */
    public Map<String, Object> concurrentWithdrawWithLock(String branchId, Long accountId,
                                                          BigDecimal amount) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> logs = Collections.synchronizedList(new ArrayList<>());

        result.put("scenario", "Concurrent Withdraw WITH Pessimistic Lock (SELECT FOR UPDATE)");

        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);

        // Lấy balance ban đầu
        BigDecimal initialBalance = jdbc.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?",
                BigDecimal.class, accountId);
        logs.add("[BEFORE] Balance = " + initialBalance);
        logs.add("Launching 2 threads, each withdrawing " + amount);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);  // Đảm bảo 2 thread chạy đồng thời
        List<Future<String>> futures = new ArrayList<>();

        // Tạo 2 thread
        for (int i = 1; i <= 2; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                startLatch.await();  // Đợi signal
                PlatformTransactionManager txManager = siteRouter.getTransactionManager(branchId);
                TransactionStatus txStatus = txManager.getTransaction(new DefaultTransactionDefinition());
                try {
                    JdbcTemplate threadJdbc = siteRouter.getJdbcTemplate(branchId);

                    // ⭐ PESSIMISTIC LOCK: SELECT FOR UPDATE
                    BigDecimal balance = threadJdbc.queryForObject(
                            "SELECT balance FROM account WHERE account_id = ? FOR UPDATE",
                            BigDecimal.class, accountId);

                    logs.add("[T" + threadNum + "] Acquired lock, balance = " + balance);

                    if (balance.compareTo(amount) >= 0) {
                        threadJdbc.update(
                                "UPDATE account SET balance = balance - ? WHERE account_id = ?",
                                amount, accountId);
                        BigDecimal newBalance = balance.subtract(amount);
                        txManager.commit(txStatus);
                        String msg = "[T" + threadNum + "] SUCCESS: withdrew " + amount +
                                " → balance = " + newBalance + ", COMMITTED";
                        logs.add(msg);
                        return "SUCCESS";
                    } else {
                        txManager.rollback(txStatus);
                        String msg = "[T" + threadNum + "] FAILED: insufficient balance (" + balance + " < " + amount + ")";
                        logs.add(msg);
                        return "FAILED";
                    }
                } catch (Exception e) {
                    if (!txStatus.isCompleted()) txManager.rollback(txStatus);
                    logs.add("[T" + threadNum + "] ERROR: " + e.getMessage());
                    return "ERROR";
                }
            }));
        }

        // Signal: cả 2 thread bắt đầu đồng thời
        startLatch.countDown();

        // Đợi kết quả
        for (int i = 0; i < futures.size(); i++) {
            try {
                String threadResult = futures.get(i).get(10, TimeUnit.SECONDS);
                result.put("thread" + (i + 1) + "Result", threadResult);
            } catch (Exception e) {
                result.put("thread" + (i + 1) + "Result", "TIMEOUT/ERROR");
            }
        }
        executor.shutdown();

        // Kiểm tra balance cuối
        BigDecimal finalBalance = jdbc.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?",
                BigDecimal.class, accountId);
        BigDecimal expectedBalance = initialBalance.subtract(amount.multiply(BigDecimal.valueOf(2)));

        logs.add("[AFTER] Balance = " + finalBalance);
        logs.add("Expected = " + expectedBalance + " | Actual = " + finalBalance);

        boolean correct = finalBalance.compareTo(expectedBalance) == 0;
        if (correct) {
            logs.add("✅ CORRECT: Pessimistic locking prevented Lost Update!");
        } else {
            logs.add("❌ INCORRECT: Lost Update detected!");
        }

        result.put("initialBalance", initialBalance);
        result.put("expectedBalance", expectedBalance);
        result.put("actualBalance", finalBalance);
        result.put("correct", correct);
        result.put("logs", logs);

        return result;
    }

    // ============================================================
    // DEMO CONCURRENT WITHDRAW — WITHOUT LOCK (để so sánh)
    // ============================================================

    /**
     * Demo 2 thread cùng rút tiền KHÔNG CÓ lock.
     * Kết quả CÓ THỂ SAI: Lost Update xảy ra.
     */
    public Map<String, Object> concurrentWithdrawWithoutLock(String branchId, Long accountId,
                                                              BigDecimal amount) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> logs = Collections.synchronizedList(new ArrayList<>());

        result.put("scenario", "Concurrent Withdraw WITHOUT Lock (Lost Update possible)");

        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);

        BigDecimal initialBalance = jdbc.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?",
                BigDecimal.class, accountId);
        logs.add("[BEFORE] Balance = " + initialBalance);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 1; i <= 2; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    JdbcTemplate threadJdbc = siteRouter.getJdbcTemplate(branchId);

                    // ⚠️ NO LOCK — đọc balance bình thường
                    BigDecimal balance = threadJdbc.queryForObject(
                            "SELECT balance FROM account WHERE account_id = ?",
                            BigDecimal.class, accountId);

                    logs.add("[T" + threadNum + "] Read balance = " + balance + " (NO LOCK)");

                    // Simulate xử lý chậm
                    Thread.sleep(100);

                    if (balance.compareTo(amount) >= 0) {
                        // ⚠️ UPDATE trực tiếp — có thể overwrite thread khác
                        BigDecimal newBalance = balance.subtract(amount);
                        threadJdbc.update(
                                "UPDATE account SET balance = ? WHERE account_id = ?",
                                newBalance, accountId);
                        logs.add("[T" + threadNum + "] Set balance = " + newBalance + " (DIRECT UPDATE)");
                        return "SUCCESS";
                    } else {
                        logs.add("[T" + threadNum + "] FAILED: insufficient");
                        return "FAILED";
                    }
                } catch (Exception e) {
                    logs.add("[T" + threadNum + "] ERROR: " + e.getMessage());
                    return "ERROR";
                }
            }));
        }

        startLatch.countDown();

        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // ignore
            }
        }
        executor.shutdown();

        BigDecimal finalBalance = jdbc.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?",
                BigDecimal.class, accountId);
        BigDecimal expectedBalance = initialBalance.subtract(amount.multiply(BigDecimal.valueOf(2)));

        logs.add("[AFTER] Balance = " + finalBalance);
        logs.add("Expected = " + expectedBalance + " | Actual = " + finalBalance);

        boolean correct = finalBalance.compareTo(expectedBalance) == 0;
        if (!correct) {
            logs.add("❌ LOST UPDATE DETECTED! Balance is wrong!");
            logs.add("💡 This is why we need SELECT FOR UPDATE (pessimistic locking)");
        } else {
            logs.add("⚠️ No Lost Update this time (race condition is non-deterministic)");
        }

        result.put("initialBalance", initialBalance);
        result.put("expectedBalance", expectedBalance);
        result.put("actualBalance", finalBalance);
        result.put("correct", correct);
        result.put("logs", logs);

        // Restore balance
        jdbc.update("UPDATE account SET balance = ? WHERE account_id = ?",
                initialBalance, accountId);
        logs.add("[RESTORE] Balance restored to " + initialBalance);

        return result;
    }
}
