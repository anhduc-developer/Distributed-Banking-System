package dev.distributed.bank.dto.request;

/**
 * DTO: Request tạo khách hàng mới.
 * Client gửi JSON body này khi gọi POST /api/customers
 */
public class CreateCustomerRequest {

    private String fullName;    // Bắt buộc
    private String phone;       // Bắt buộc, unique
    private String email;       // Tuỳ chọn
    private String address;     // Tuỳ chọn
    private String branchId;    // Bắt buộc: "HN", "DN", "HCM"

    // Getters & Setters

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }
}
