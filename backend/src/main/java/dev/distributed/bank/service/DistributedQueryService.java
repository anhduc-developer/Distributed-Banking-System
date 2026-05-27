package dev.distributed.bank.service;

import dev.distributed.bank.distributed.SiteRouter;
import dev.distributed.bank.dto.response.TopCustomerResponse;
import dev.distributed.bank.dto.response.TotalBalanceResponse;
import dev.distributed.bank.dto.response.TransactionStatsResponse;
import dev.distributed.bank.entity.TransactionHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service: Distributed Query — Truy vấn phân tán.
 *
 * Tất cả query trong service này là DISTRIBUTED QUERY:
 * - Chạy cùng 1 câu query trên CẢ 3 site
 * - Coordinator (Spring Boot) merge kết quả ở tầng ứng dụng
 *
 * Đây là phần quan trọng cho đồ án CSDL phân tán — thể hiện rõ
 * việc query phải chạy ở NHIỀU site rồi tổng hợp lại.
 */
@Service
public class DistributedQueryService {

    private final SiteRouter siteRouter;

    public DistributedQueryService(SiteRouter siteRouter) {
        this.siteRouter = siteRouter;
    }

    // ============================================================
    // QUERY 1: Tổng số dư toàn hệ thống
    // Flow: SUM(balance) ở mỗi site → cộng 3 kết quả
    // ============================================================

    public TotalBalanceResponse getTotalBalance() {
        System.out.println("═══ [DISTRIBUTED QUERY] Total Balance ═══");

        TotalBalanceResponse response = new TotalBalanceResponse();
        BigDecimal systemTotal = BigDecimal.ZERO;

        // Query Site HN
        try {
            JdbcTemplate hnJdbc = siteRouter.getJdbcTemplate("HN");
            BigDecimal hnBalance = hnJdbc.queryForObject(
                    "SELECT COALESCE(SUM(balance), 0) FROM account WHERE status = 'ACTIVE'",
                    BigDecimal.class);
            int hnCount = hnJdbc.queryForObject(
                    "SELECT COUNT(*) FROM account WHERE status = 'ACTIVE'", Integer.class);
            response.setHanoiTotalBalance(hnBalance);
            response.setHanoiAccountCount(hnCount);
            systemTotal = systemTotal.add(hnBalance);
            System.out.println("  Site HN: " + hnBalance + " (" + hnCount + " accounts)");
        } catch (Exception e) {
            response.setHanoiTotalBalance(BigDecimal.ZERO);
            System.out.println("  Site HN: UNREACHABLE");
        }

        // Query Site DN
        try {
            JdbcTemplate dnJdbc = siteRouter.getJdbcTemplate("DN");
            BigDecimal dnBalance = dnJdbc.queryForObject(
                    "SELECT COALESCE(SUM(balance), 0) FROM account WHERE status = 'ACTIVE'",
                    BigDecimal.class);
            int dnCount = dnJdbc.queryForObject(
                    "SELECT COUNT(*) FROM account WHERE status = 'ACTIVE'", Integer.class);
            response.setDanangTotalBalance(dnBalance);
            response.setDanangAccountCount(dnCount);
            systemTotal = systemTotal.add(dnBalance);
            System.out.println("  Site DN: " + dnBalance + " (" + dnCount + " accounts)");
        } catch (Exception e) {
            response.setDanangTotalBalance(BigDecimal.ZERO);
            System.out.println("  Site DN: UNREACHABLE");
        }

        // Query Site HCM
        try {
            JdbcTemplate hcmJdbc = siteRouter.getJdbcTemplate("HCM");
            BigDecimal hcmBalance = hcmJdbc.queryForObject(
                    "SELECT COALESCE(SUM(balance), 0) FROM account WHERE status = 'ACTIVE'",
                    BigDecimal.class);
            int hcmCount = hcmJdbc.queryForObject(
                    "SELECT COUNT(*) FROM account WHERE status = 'ACTIVE'", Integer.class);
            response.setHcmTotalBalance(hcmBalance);
            response.setHcmAccountCount(hcmCount);
            systemTotal = systemTotal.add(hcmBalance);
            System.out.println("  Site HCM: " + hcmBalance + " (" + hcmCount + " accounts)");
        } catch (Exception e) {
            response.setHcmTotalBalance(BigDecimal.ZERO);
            System.out.println("  Site HCM: UNREACHABLE");
        }

        response.setSystemTotalBalance(systemTotal);
        System.out.println("  TOTAL: " + systemTotal);
        System.out.println("═══════════════════════════════════════════");

        return response;
    }

    // ============================================================
    // QUERY 2: Top N khách hàng giàu nhất
    // Flow: Top-K từ mỗi site → merge → sort → lấy top N
    // ============================================================

    public List<TopCustomerResponse> getTopCustomers(int limit) {
        System.out.println("═══ [DISTRIBUTED QUERY] Top " + limit + " Customers ═══");

        List<TopCustomerResponse> allCustomers = new ArrayList<>();

        String sql = "SELECT c.customer_id, c.full_name, c.branch_id, " +
                     "SUM(a.balance) as total_balance, COUNT(a.account_id) as account_count " +
                     "FROM customer c JOIN account a ON c.customer_id = a.customer_id " +
                     "WHERE a.status = 'ACTIVE' " +
                     "GROUP BY c.customer_id, c.full_name, c.branch_id " +
                     "ORDER BY total_balance DESC LIMIT ?";

        RowMapper<TopCustomerResponse> mapper = (rs, rowNum) -> new TopCustomerResponse(
                rs.getLong("customer_id"),
                rs.getString("full_name"),
                rs.getString("branch_id"),
                rs.getBigDecimal("total_balance"),
                rs.getInt("account_count")
        );

        // Query mỗi site, lấy top-K local
        for (String branchId : siteRouter.getAllBranchIds()) {
            try {
                JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
                List<TopCustomerResponse> localTop = jdbc.query(sql, mapper, limit);
                allCustomers.addAll(localTop);
                System.out.println("  Site " + branchId + ": returned " + localTop.size() + " customers");
            } catch (Exception e) {
                System.out.println("  Site " + branchId + ": UNREACHABLE");
            }
        }

        // Merge: sort tất cả theo total_balance DESC, lấy top N
        List<TopCustomerResponse> result = allCustomers.stream()
                .sorted((a, b) -> b.getTotalBalance().compareTo(a.getTotalBalance()))
                .limit(limit)
                .collect(Collectors.toList());

        System.out.println("  Merged result: " + result.size() + " customers");
        System.out.println("═══════════════════════════════════════════");

        return result;
    }

    // ============================================================
    // QUERY 3: Giao dịch liên chi nhánh gần đây
    // ============================================================

    public List<TransactionHistory> getInterBranchTransactions() {
        System.out.println("═══ [DISTRIBUTED QUERY] Inter-Branch Transactions ═══");

        List<TransactionHistory> allTxns = new ArrayList<>();

        RowMapper<TransactionHistory> mapper = (rs, rowNum) -> {
            TransactionHistory t = new TransactionHistory();
            t.setTransactionId(rs.getLong("transaction_id"));
            t.setTransactionType(rs.getString("transaction_type"));
            t.setAmount(rs.getBigDecimal("amount"));
            t.setAccountId(rs.getLong("account_id"));
            long relId = rs.getLong("related_account_id");
            t.setRelatedAccountId(rs.wasNull() ? null : relId);
            t.setRelatedBranchId(rs.getString("related_branch_id"));
            t.setBalanceAfter(rs.getBigDecimal("balance_after"));
            t.setStatus(rs.getString("status"));
            t.setDistributedTxnId(rs.getString("distributed_txn_id"));
            t.setDescription(rs.getString("description"));
            t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return t;
        };

        for (String branchId : siteRouter.getAllBranchIds()) {
            try {
                JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
                List<TransactionHistory> txns = jdbc.query(
                        "SELECT * FROM transaction_history " +
                        "WHERE transaction_type IN ('INTER_BRANCH_IN', 'INTER_BRANCH_OUT') " +
                        "ORDER BY created_at DESC LIMIT 50",
                        mapper);
                allTxns.addAll(txns);
            } catch (Exception e) {
                System.out.println("  Site " + branchId + ": UNREACHABLE");
            }
        }

        // Sort by created_at DESC
        allTxns.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        System.out.println("  Total inter-branch transactions: " + allTxns.size());
        System.out.println("═══════════════════════════════════════════");

        return allTxns;
    }

    // ============================================================
    // QUERY 4: Khách hàng có tài khoản ở nhiều chi nhánh
    // Flow: Lấy phone từ mỗi site → group by phone → filter ≥ 2
    // ============================================================

    public List<Map<String, Object>> getMultiBranchCustomers() {
        System.out.println("═══ [DISTRIBUTED QUERY] Multi-Branch Customers ═══");

        // Map: customerId → list of {branchId, fullName, totalBalance, accountCount}
        Map<Long, List<Map<String, Object>>> customerMap = new HashMap<>();

        for (String branchId : siteRouter.getAllBranchIds()) {
            try {
                JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);

                // Lấy tất cả customer có account ở chi nhánh này
                List<Map<String, Object>> customers = jdbc.queryForList(
                        "SELECT c.customer_id, c.full_name, c.phone, " +
                        "COUNT(a.account_id) as account_count, " +
                        "COALESCE(SUM(a.balance), 0) as total_balance " +
                        "FROM customer c " +
                        "JOIN account a ON c.customer_id = a.customer_id " +
                        "WHERE a.status = 'ACTIVE' " +
                        "GROUP BY c.customer_id, c.full_name, c.phone"
                );

                for (Map<String, Object> c : customers) {
                    Long customerId = ((Number) c.get("customer_id")).longValue();
                    Map<String, Object> info = new HashMap<>();
                    info.put("branchId", branchId);
                    info.put("fullName", c.get("full_name"));
                    info.put("phone", c.get("phone"));
                    info.put("accountCount", ((Number) c.get("account_count")).intValue());
                    info.put("totalBalance", c.get("total_balance"));
                    customerMap.computeIfAbsent(customerId, k -> new ArrayList<>()).add(info);
                }

                System.out.println("  Site " + branchId + ": " + customers.size() + " customers with active accounts");
            } catch (Exception e) {
                System.out.println("  Site " + branchId + ": UNREACHABLE");
            }
        }

        // Filter: chỉ lấy customer_id xuất hiện ở ≥ 2 chi nhánh
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<Map<String, Object>>> entry : customerMap.entrySet()) {
            // Đếm số chi nhánh khác nhau
            long distinctBranches = entry.getValue().stream()
                    .map(m -> (String) m.get("branchId"))
                    .distinct()
                    .count();

            if (distinctBranches >= 2) {
                Map<String, Object> item = new HashMap<>();
                item.put("customerId", entry.getKey());
                item.put("branches", entry.getValue());
                item.put("branchCount", distinctBranches);

                // Lấy tên từ bất kỳ branch nào
                item.put("fullName", entry.getValue().get(0).get("fullName"));
                item.put("phone", entry.getValue().get(0).get("phone"));

                result.add(item);
            }
        }

        // Sort by branchCount DESC
        result.sort((a, b) -> Long.compare(
                (long) b.get("branchCount"),
                (long) a.get("branchCount")));

        System.out.println("  Customers with multi-branch accounts: " + result.size());
        System.out.println("═══════════════════════════════════════════");

        return result;
    }

    // ============================================================
    // QUERY 5: Thống kê giao dịch theo chi nhánh
    // ============================================================

    public List<TransactionStatsResponse> getTransactionStats() {
        System.out.println("═══ [DISTRIBUTED QUERY] Transaction Stats ═══");

        List<TransactionStatsResponse> stats = new ArrayList<>();
        Map<String, String> branchNames = Map.of(
                "HN", "Chi nhánh Hà Nội",
                "DN", "Chi nhánh Đà Nẵng",
                "HCM", "Chi nhánh TP.HCM"
        );

        for (String branchId : siteRouter.getAllBranchIds()) {
            try {
                JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);

                TransactionStatsResponse stat = new TransactionStatsResponse();
                stat.setBranchId(branchId);
                stat.setBranchName(branchNames.getOrDefault(branchId, branchId));

                // Total transactions
                Long total = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transaction_history", Long.class);
                stat.setTotalTransactions(total != null ? total : 0);

                // Deposit count & amount
                Map<String, Object> depositStats = jdbc.queryForMap(
                        "SELECT COUNT(*) as cnt, COALESCE(SUM(amount), 0) as total_amount " +
                        "FROM transaction_history WHERE transaction_type = 'DEPOSIT'");
                stat.setDepositCount(((Number) depositStats.get("cnt")).longValue());
                stat.setTotalDepositAmount((BigDecimal) depositStats.get("total_amount"));

                // Withdraw count & amount
                Map<String, Object> withdrawStats = jdbc.queryForMap(
                        "SELECT COUNT(*) as cnt, COALESCE(SUM(amount), 0) as total_amount " +
                        "FROM transaction_history WHERE transaction_type = 'WITHDRAW'");
                stat.setWithdrawCount(((Number) withdrawStats.get("cnt")).longValue());
                stat.setTotalWithdrawAmount((BigDecimal) withdrawStats.get("total_amount"));

                // Transfer count
                Long transferCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transaction_history WHERE transaction_type LIKE '%TRANSFER%'",
                        Long.class);
                stat.setTransferCount(transferCount != null ? transferCount : 0);

                stats.add(stat);
                System.out.println("  Site " + branchId + ": " + total + " transactions");

            } catch (Exception e) {
                System.out.println("  Site " + branchId + ": UNREACHABLE");
            }
        }

        System.out.println("═══════════════════════════════════════════");
        return stats;
    }
}
