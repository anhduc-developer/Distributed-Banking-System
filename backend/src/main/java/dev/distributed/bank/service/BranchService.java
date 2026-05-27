package dev.distributed.bank.service;

import dev.distributed.bank.distributed.SiteRouter;
import dev.distributed.bank.entity.Branch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service: Quản lý chi nhánh ngân hàng.
 *
 * Branch là bảng đơn giản nhất — mỗi site chỉ có 1 record.
 * Service này query tất cả 3 site để lấy danh sách chi nhánh.
 */
@Service
public class BranchService {

    private final SiteRouter siteRouter;

    /** RowMapper: chuyển ResultSet → Branch object */
    private final RowMapper<Branch> branchRowMapper = (rs, rowNum) -> {
        Branch branch = new Branch();
        branch.setBranchId(rs.getString("branch_id"));
        branch.setBranchName(rs.getString("branch_name"));
        branch.setCity(rs.getString("city"));
        branch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return branch;
    };

    public BranchService(SiteRouter siteRouter) {
        this.siteRouter = siteRouter;
    }

    /**
     * Lấy danh sách TẤT CẢ chi nhánh — distributed query.
     * Query mỗi site 1 record, merge 3 results.
     */
    public List<Branch> getAllBranches() {
        List<Branch> allBranches = new ArrayList<>();

        // Query từng site và gom kết quả
        for (String branchId : siteRouter.getAllBranchIds()) {
            try {
                JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
                List<Branch> branches = jdbc.query("SELECT * FROM branch", branchRowMapper);
                allBranches.addAll(branches);
            } catch (Exception e) {
                System.out.println("⚠️ Cannot reach site " + branchId + ": " + e.getMessage());
            }
        }

        return allBranches;
    }

    /**
     * Lấy chi tiết 1 chi nhánh — query trực tiếp site đó.
     */
    public Branch getBranchById(String branchId) {
        JdbcTemplate jdbc = siteRouter.getJdbcTemplate(branchId);
        List<Branch> branches = jdbc.query(
                "SELECT * FROM branch WHERE branch_id = ?",
                branchRowMapper,
                branchId.toUpperCase()
        );
        return branches.isEmpty() ? null : branches.get(0);
    }
}
