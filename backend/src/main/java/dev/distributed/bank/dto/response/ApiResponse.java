package dev.distributed.bank.dto.response;

/**
 * API Response Wrapper — format THỐNG NHẤT cho tất cả API.
 *
 * Mọi API đều trả về dạng:
 * {
 *   "success": true/false,
 *   "message": "...",
 *   "data": { ... }    ← dữ liệu thực tế (generic type T)
 * }
 *
 * Lợi ích:
 * - Client luôn biết request thành công hay thất bại qua field "success"
 * - Luôn có "message" để hiển thị cho user
 * - "data" chứa dữ liệu thực tế, type-safe nhờ generic
 *
 * Ví dụ sử dụng:
 *   return ApiResponse.ok("Tạo thành công", customer);
 *   return ApiResponse.error("Không tìm thấy");
 */
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    // ============================================================
    // Constructors
    // ============================================================

    public ApiResponse() {
    }

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    // ============================================================
    // Static factory methods — dùng thay cho constructor
    // ============================================================

    /** Trả response thành công với data */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    /** Trả response thành công với message tùy chỉnh + data */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /** Trả response lỗi (không có data) */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    // ============================================================
    // Getters & Setters
    // ============================================================

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
