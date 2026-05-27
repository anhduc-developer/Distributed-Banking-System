package dev.distributed.bank.exception;

import dev.distributed.bank.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Xử lý lỗi tập trung cho TOÀN BỘ ứng dụng.
 *
 * Khi bất kỳ controller nào throw exception, class này bắt và trả về
 * JSON response format thống nhất:
 * {
 *   "success": false,
 *   "message": "Mô tả lỗi",
 *   "data": null
 * }
 *
 * Lợi ích: Client luôn nhận cùng 1 format, dù thành công hay lỗi.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /** Lỗi: Không đủ số dư → HTTP 400 */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientBalance(InsufficientBalanceException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Lỗi: Không tìm thấy tài khoản → HTTP 404 */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotFound(AccountNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Lỗi: Site bị down (mô phỏng) → HTTP 503 */
    @ExceptionHandler(SiteDownException.class)
    public ResponseEntity<ApiResponse<Void>> handleSiteDown(SiteDownException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Lỗi: Tài khoản không ACTIVE → HTTP 403 */
    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountInactive(AccountInactiveException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Lỗi: Argument không hợp lệ → HTTP 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Lỗi chung — bắt tất cả exception còn lại → HTTP 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        ex.printStackTrace(); // Log ra console để debug
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error: " + ex.getMessage()));
    }
}
