package dev.distributed.bank.entity;

import java.time.LocalDateTime;

/**
 * Entity: Chi nhánh ngân hàng.
 * Mỗi site chỉ có 1 record branch (chi nhánh mình).
 *
 * Vì dùng JdbcTemplate (không phải JPA), đây chỉ là POJO đơn giản
 * — không cần @Entity, @Table, @Column.
 */
public class Branch {

    private String branchId;      // PK: "HN", "DN", "HCM"
    private String branchName;    // "Chi nhánh Hà Nội"
    private String city;          // "Hà Nội"
    private LocalDateTime createdAt;

    // ============================================================
    // Constructors
    // ============================================================

    public Branch() {
    }

    public Branch(String branchId, String branchName, String city) {
        this.branchId = branchId;
        this.branchName = branchName;
        this.city = city;
    }

    // ============================================================
    // Getters & Setters
    // ============================================================

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
