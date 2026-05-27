package dev.distributed.bank.distributed;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;
import java.util.List;

/**
 * SiteRouter — Bộ định tuyến trung tâm.
 *
 * Vai trò: Cho biết branchId nào → dùng JdbcTemplate nào.
 * Đây là thành phần quan trọng nhất trong hệ thống phân tán:
 * khi service cần truy vấn 1 site, nó hỏi SiteRouter để lấy
 * đúng JdbcTemplate (tức đúng database connection).
 *
 * Ví dụ:
 * siteRouter.getJdbcTemplate("HN") → JdbcTemplate kết nối MySQL Hà Nội
 * siteRouter.getJdbcTemplate("HCM") → JdbcTemplate kết nối MySQL TP.HCM
 *
 * Tương đương trong thực tế: Tổng đài ngân hàng — bạn nói "chi nhánh Hà Nội",
 * tổng đài nối bạn đến đúng chi nhánh.
 */
@Component
public class SiteRouter {

    private final JdbcTemplate hanoiJdbcTemplate;
    private final JdbcTemplate danangJdbcTemplate;
    private final JdbcTemplate hcmJdbcTemplate;

    private final PlatformTransactionManager hanoiTxManager;
    private final PlatformTransactionManager danangTxManager;
    private final PlatformTransactionManager hcmTxManager;

    /** Flag mô phỏng site down — toggle qua API demo */
    private volatile String simulatedDownSite = null;

    public SiteRouter(
            @Qualifier("hanoiJdbcTemplate") JdbcTemplate hanoiJdbcTemplate,
            @Qualifier("danangJdbcTemplate") JdbcTemplate danangJdbcTemplate,
            @Qualifier("hcmJdbcTemplate") JdbcTemplate hcmJdbcTemplate,
            @Qualifier("hanoiTransactionManager") PlatformTransactionManager hanoiTxManager,
            @Qualifier("danangTransactionManager") PlatformTransactionManager danangTxManager,
            @Qualifier("hcmTransactionManager") PlatformTransactionManager hcmTxManager) {
        this.hanoiJdbcTemplate = hanoiJdbcTemplate;
        this.danangJdbcTemplate = danangJdbcTemplate;
        this.hcmJdbcTemplate = hcmJdbcTemplate;
        this.hanoiTxManager = hanoiTxManager;
        this.danangTxManager = danangTxManager;
        this.hcmTxManager = hcmTxManager;
    }

    /**
     * Lấy JdbcTemplate theo branchId.
     * Đây là hàm quan trọng nhất — tất cả service đều gọi hàm này.
     *
     * @param branchId Mã chi nhánh: "HN", "DN", "HCM"
     * @return JdbcTemplate kết nối đến database của chi nhánh đó
     * @throws dev.distributed.bank.exception.SiteDownException nếu site đang bị mô
     *                                                          phỏng down
     * @throws IllegalArgumentException                         nếu branchId không
     *                                                          hợp lệ
     */
    public JdbcTemplate getJdbcTemplate(String branchId) {
        // Kiểm tra site có đang bị mô phỏng down không
        if (branchId.equals(simulatedDownSite)) {
            throw new dev.distributed.bank.exception.SiteDownException(
                    "Site " + branchId + " is DOWN (simulated)");
        }

        return switch (branchId.toUpperCase()) {
            case "HN" -> hanoiJdbcTemplate;
            case "DN" -> danangJdbcTemplate;
            case "HCM" -> hcmJdbcTemplate;
            default -> throw new IllegalArgumentException(
                    "Unknown branch: " + branchId + ". Valid: HN, DN, HCM");
        };
    }

    /**
     * Lấy TransactionManager theo branchId.
     * Dùng khi cần quản lý transaction thủ công (BEGIN/COMMIT/ROLLBACK).
     */
    public PlatformTransactionManager getTransactionManager(String branchId) {
        if (branchId.equals(simulatedDownSite)) {
            throw new dev.distributed.bank.exception.SiteDownException(
                    "Site " + branchId + " is DOWN (simulated)");
        }

        return switch (branchId.toUpperCase()) {
            case "HN" -> hanoiTxManager;
            case "DN" -> danangTxManager;
            case "HCM" -> hcmTxManager;
            default -> throw new IllegalArgumentException(
                    "Unknown branch: " + branchId);
        };
    }

    /**
     * Lấy JdbcTemplate của TẤT CẢ sites.
     * Dùng cho distributed query (query cả 3 site rồi merge).
     */
    public List<JdbcTemplate> getAllJdbcTemplates() {
        return Arrays.asList(hanoiJdbcTemplate, danangJdbcTemplate, hcmJdbcTemplate);
    }

    /** Lấy danh sách tất cả mã chi nhánh */
    public List<String> getAllBranchIds() {
        return Arrays.asList("HN", "DN", "HCM");
    }

    // ============================================================
    // Phần mô phỏng lỗi (Failure Simulation)
    // ============================================================

    /** Bật mô phỏng site down */
    public void simulateSiteDown(String branchId) {
        this.simulatedDownSite = branchId.toUpperCase();
        System.out.println("⚠️ [SIMULATION] Site " + branchId + " is now DOWN");
    }

    /** Tắt mô phỏng site down */
    public void clearSiteDown() {
        System.out.println("✅ [SIMULATION] All sites are now UP");
        this.simulatedDownSite = null;
    }

    /** Kiểm tra site nào đang bị mô phỏng down */
    public String getSimulatedDownSite() {
        return simulatedDownSite;
    }
}
