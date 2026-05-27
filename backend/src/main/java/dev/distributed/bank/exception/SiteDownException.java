package dev.distributed.bank.exception;

/**
 * Exception khi site (chi nhánh) không thể kết nối.
 * Dùng cho mô phỏng lỗi site down trong demo.
 */
public class SiteDownException extends RuntimeException {
    public SiteDownException(String message) {
        super(message);
    }
}
