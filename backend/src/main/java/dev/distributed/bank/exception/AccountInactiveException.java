package dev.distributed.bank.exception;

/**
 * Exception khi tài khoản không ở trạng thái ACTIVE.
 * Ném khi cố deposit/withdraw vào tài khoản INACTIVE hoặc FROZEN.
 */
public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException(String message) {
        super(message);
    }
}
