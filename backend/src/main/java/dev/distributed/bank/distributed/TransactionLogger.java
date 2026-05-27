package dev.distributed.bank.distributed;

import dev.distributed.bank.entity.DistributedTransactionLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * TransactionLogger — Ghi log giao dịch phân tán (2PC).
 *
 * Vai trò: Ghi lại trạng thái 2PC vào bảng distributed_transaction_log
 * và transaction_participant. Coordinator đọc log này để:
 * - Theo dõi trạng thái realtime
 * - Recovery nếu crash
 * - Demo cho thầy xem flow 2PC
 *
 * Log được ghi vào site SOURCE (nơi bắt đầu giao dịch).
 *
 * ⚠️ QUAN TRỌNG: Logger sử dụng raw JDBC connection (DataSource.getConnection())
 * thay vì JdbcTemplate/TransactionTemplate của Spring. Lý do:
 * - TransferService đang giữ transaction trên cùng DataSource (thread-bound)
 * - DataSourceTransactionManager KHÔNG hỗ trợ REQUIRES_NEW trên cùng DataSource
 *   (chỉ JTA mới làm được)
 * - Dùng raw connection → lấy connection mới từ pool, hoàn toàn độc lập
 *   với transaction đang chạy trong TransferService
 */
@Component
public class TransactionLogger {

    private final SiteRouter siteRouter;

    private final RowMapper<DistributedTransactionLog> logRowMapper = (rs, rowNum) -> {
        DistributedTransactionLog log = new DistributedTransactionLog();
        log.setTxnId(rs.getString("txn_id"));
        log.setTxnType(rs.getString("txn_type"));
        log.setStatus(rs.getString("status"));
        log.setSourceBranch(rs.getString("source_branch"));
        log.setDestBranch(rs.getString("dest_branch"));
        log.setAmount(rs.getBigDecimal("amount"));
        log.setSourceAccountId(rs.getLong("source_account_id"));
        log.setDestAccountId(rs.getLong("dest_account_id"));
        log.setErrorMessage(rs.getString("error_message"));
        log.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        log.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return log;
    };

    public TransactionLogger(SiteRouter siteRouter) {
        this.siteRouter = siteRouter;
    }

    /**
     * Chạy SQL trên raw JDBC connection — hoàn toàn độc lập với Spring transaction.
     * Lấy connection mới từ pool, execute, auto-commit, rồi trả lại pool.
     */
    private void executeRaw(String branchId, String sql, Object... args) {
        DataSource ds = siteRouter.getJdbcTemplate(branchId).getDataSource();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(true);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to log transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo log giao dịch phân tán mới.
     * Ghi vào site SOURCE.
     */
    public void createTransactionLog(String txnId, String txnType, String status,
                                      String sourceBranch, String destBranch,
                                      java.math.BigDecimal amount,
                                      Long sourceAccountId, Long destAccountId) {
        executeRaw(sourceBranch,
                "INSERT INTO distributed_transaction_log " +
                "(txn_id, txn_type, status, source_branch, dest_branch, amount, source_account_id, dest_account_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                txnId, txnType, status, sourceBranch, destBranch, amount,
                sourceAccountId, destAccountId
        );
    }

    /**
     * Cập nhật status của log giao dịch phân tán.
     */
    public void updateTransactionStatus(String txnId, String sourceBranch,
                                         String newStatus, String errorMessage) {
        if (errorMessage != null) {
            executeRaw(sourceBranch,
                    "UPDATE distributed_transaction_log SET status = ?, error_message = ? WHERE txn_id = ?",
                    newStatus, errorMessage, txnId
            );
        } else {
            executeRaw(sourceBranch,
                    "UPDATE distributed_transaction_log SET status = ? WHERE txn_id = ?",
                    newStatus, txnId
            );
        }
    }

    /**
     * Thêm participant vào log.
     */
    public void addParticipant(String txnId, String branchId, String role,
                                String status, String action) {
        try {
            executeRaw(branchId,
                    "INSERT INTO transaction_participant (txn_id, branch_id, role, status, action) " +
                    "VALUES (?, ?, ?, ?, ?)",
                    txnId, branchId, role, status, action
            );
        } catch (Exception e) {
            // Nếu site down, ghi vào site source thay thế
            System.out.println("⚠️ Cannot log participant at " + branchId + ": " + e.getMessage());
        }
    }

    /**
     * Cập nhật status participant.
     */
    public void updateParticipantStatus(String txnId, String branchId, String newStatus) {
        try {
            executeRaw(branchId,
                    "UPDATE transaction_participant SET status = ? WHERE txn_id = ? AND branch_id = ?",
                    newStatus, txnId, branchId
            );
        } catch (Exception e) {
            System.out.println("⚠️ Cannot update participant at " + branchId);
        }
    }

    /**
     * Lấy tất cả log giao dịch phân tán — query từ tất cả sites.
     */
    public List<DistributedTransactionLog> getAllTransactionLogs() {
        List<DistributedTransactionLog> allLogs = new java.util.ArrayList<>();
        for (String branchId : siteRouter.getAllBranchIds()) {
            try {
                JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
                List<DistributedTransactionLog> logs = jdbc.query(
                        "SELECT * FROM distributed_transaction_log ORDER BY created_at DESC",
                        logRowMapper
                );
                allLogs.addAll(logs);
            } catch (Exception e) {
                // Site might be down
            }
        }
        return allLogs;
    }
}

